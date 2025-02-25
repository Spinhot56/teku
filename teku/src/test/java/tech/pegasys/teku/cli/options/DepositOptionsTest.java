/*
 * Copyright Consensys Software Inc., 2022
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.cli.options;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.beacon.pow.DepositSnapshotFileLoader.DEFAULT_SNAPSHOT_RESOURCE_PATHS;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.teku.cli.AbstractBeaconNodeCommandTest;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.services.powchain.PowchainConfiguration;
import tech.pegasys.teku.spec.networks.Eth2Network;

public class DepositOptionsTest extends AbstractBeaconNodeCommandTest {

  @Test
  public void shouldReadDepositOptionsFromConfigurationFile() {
    final TekuConfiguration config = getTekuConfigurationFromFile("depositOptions_config.yaml");

    assertThat(config.powchain().isEnabled()).isTrue();
    assertThat(config.powchain().getEth1Endpoints())
        .containsExactly("http://example.com:1234/path/");
  }

  @Test
  public void shouldReportEth1EnabledIfEndpointSpecified() {
    final String[] args = {"--eth1-endpoint", "http://example.com:1234/path/"};
    final TekuConfiguration config = getTekuConfigurationFromArguments(args);
    assertThat(config.powchain().isEnabled()).isTrue();
    assertThat(
            createConfigBuilder()
                .powchain(b -> b.eth1Endpoints(List.of("http://example.com:1234/path/")))
                .build())
        .usingRecursiveComparison()
        .isEqualTo(config);
  }

  @Test
  public void shouldReportEth1DisabledIfEndpointNotSpecified() {
    final TekuConfiguration config = getTekuConfigurationFromArguments();
    assertThat(config.powchain().isEnabled()).isFalse();
  }

  @Test
  public void shouldReportEth1DisabledIfEndpointIsEmpty() {
    final String[] args = {"--eth1-endpoint", "   "};
    final TekuConfiguration config = getTekuConfigurationFromArguments(args);
    assertThat(config.powchain().isEnabled()).isFalse();
    assertThat(createConfigBuilder().powchain(b -> b.eth1Endpoints(List.of())).build())
        .usingRecursiveComparison()
        .isEqualTo(config);
  }

  @Test
  public void multiple_eth1Endpoints_areSupported() {
    final String[] args = {
      "--eth1-endpoints",
      "http://example.com:1234/path/,http://example-2.com:1234/path/",
      "http://example-3.com:1234/path/"
    };
    final TekuConfiguration config = getTekuConfigurationFromArguments(args);
    assertThat(config.powchain().getEth1Endpoints())
        .containsExactlyInAnyOrder(
            "http://example.com:1234/path/",
            "http://example-2.com:1234/path/",
            "http://example-3.com:1234/path/");
    assertThat(config.powchain().isEnabled()).isTrue();
    assertThat(
            createConfigBuilder()
                .powchain(
                    b ->
                        b.eth1Endpoints(
                            List.of(
                                "http://example.com:1234/path/",
                                "http://example-2.com:1234/path/",
                                "http://example-3.com:1234/path/")))
                .build())
        .usingRecursiveComparison()
        .isEqualTo(config);
  }

  @Test
  public void multiple_eth1Endpoints_areSupported_mixedParams() {
    final String[] args = {
      "--eth1-endpoint",
      "http://example-single.com:1234/path/",
      "--eth1-endpoints",
      "http://example.com:1234/path/,http://example-2.com:1234/path/",
      "http://example-3.com:1234/path/"
    };
    final TekuConfiguration config = getTekuConfigurationFromArguments(args);
    assertThat(config.powchain().getEth1Endpoints())
        .containsExactlyInAnyOrder(
            "http://example-single.com:1234/path/",
            "http://example.com:1234/path/",
            "http://example-2.com:1234/path/",
            "http://example-3.com:1234/path/");
    assertThat(config.powchain().isEnabled()).isTrue();
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"mainnet", "goerli", "prater", "gnosis", "sepolia"})
  public void shouldSetDefaultBundleSnapshotPathForSupportedNetwork(final String network) {
    final String[] args = {"--network=" + network, "--deposit-snapshot-enabled"};
    final TekuConfiguration config = getTekuConfigurationFromArguments(args);
    assertThat(config.powchain().isDepositSnapshotEnabled()).isTrue();
    assertThat(config.powchain().getDepositSnapshotPath())
        .contains(getDefaultBundleSnapshotPath(network));
  }

  @Test
  public void shouldIgnoreBundleSnapshotPathForNotSupportedNetwork() {
    final String[] args = {"--network=swift", "--deposit-snapshot-enabled"};
    final TekuConfiguration config = getTekuConfigurationFromArguments(args);
    assertThat(config.powchain().isDepositSnapshotEnabled()).isTrue();
    assertThat(config.powchain().getDepositSnapshotPath()).isEmpty();
  }

  @Test
  public void shouldThrowErrorIfSnapshotPathProvidedWithBundleSnapshot() {
    assertThatThrownBy(
            () ->
                createConfigBuilder()
                    .eth2NetworkConfig(b -> b.applyNetworkDefaults(Eth2Network.MAINNET))
                    .powchain(b -> b.depositSnapshotEnabled(true).depositSnapshotPath("/some/path"))
                    .build())
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessage("Use either custom deposit tree snapshot path or snapshot bundle");
  }

  @ParameterizedTest
  @MethodSource("getDepositSnapshotOptions")
  public void shouldHandleSnapshotPathAndEnabledConfig(
      final String[] args, final String expectedPath, final boolean expectedIsEnabled) {
    final PowchainConfiguration config = getTekuConfigurationFromArguments(args).powchain();
    assertThat(config.getDepositSnapshotPath()).isEqualTo(Optional.of(expectedPath));
    assertThat(config.isDepositSnapshotEnabled()).isEqualTo(expectedIsEnabled);
  }

  public static Stream<Arguments> getDepositSnapshotOptions() {
    final String[] emptyConfig = {"--network=mainnet"};
    final String[] depositSnapshotEnabledSet = {
      "--network=mainnet",
      "--deposit-snapshot-enabled=true",
      "--checkpoint-sync-url=http://checkpoint/path/"
    };
    final String[] depositSnapshotPathSet = {"--Xdeposit-snapshot=/some/path/"};
    final String[] depositSnapshotPathSetAndCheckpointSync = {
      "--Xdeposit-snapshot=/some/path/",
      "--deposit-snapshot-enabled=false",
      "--checkpoint-sync-url=http://checkpoint/path/"
    };
    final String[] depositSnapshotDisabledAndSync = {
      "--deposit-snapshot-enabled=false", "--checkpoint-sync-url=http://checkpoint/path/"
    };
    final String[] checkpointSyncUrlSet = {"--checkpoint-sync-url=http://checkpoint/path/"};
    final String defaultBundleSnapshotPath = getDefaultBundleSnapshotPath("mainnet");

    return Stream.of(
        Arguments.of(emptyConfig, defaultBundleSnapshotPath, true),
        Arguments.of(depositSnapshotEnabledSet, defaultBundleSnapshotPath, true),
        Arguments.of(depositSnapshotPathSet, "/some/path/", false),
        Arguments.of(depositSnapshotPathSetAndCheckpointSync, "/some/path/", false),
        Arguments.of(
            depositSnapshotDisabledAndSync,
            "http://checkpoint/eth/v1/beacon/deposit_snapshot",
            false),
        Arguments.of(
            checkpointSyncUrlSet, "http://checkpoint/eth/v1/beacon/deposit_snapshot", false));
  }

  private static String getDefaultBundleSnapshotPath(final String network) {
    return PowchainConfiguration.class
        .getResource(
            DEFAULT_SNAPSHOT_RESOURCE_PATHS.get(Eth2Network.fromStringLenient(network).get()))
        .toExternalForm();
  }
}
