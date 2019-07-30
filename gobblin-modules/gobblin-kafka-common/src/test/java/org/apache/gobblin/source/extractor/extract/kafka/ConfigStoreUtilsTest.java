package org.apache.gobblin.source.extractor.extract.kafka;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import org.apache.gobblin.config.client.ConfigClient;
import org.apache.gobblin.config.client.api.VersionStabilityPolicy;
import org.apache.gobblin.kafka.client.GobblinKafkaConsumerClient;
import org.apache.hadoop.fs.Path;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import static org.apache.gobblin.source.extractor.extract.kafka.ConfigStoreUtils.GOBBLIN_CONFIG_COMMONPATH;
import static org.apache.gobblin.source.extractor.extract.kafka.ConfigStoreUtils.GOBBLIN_CONFIG_FILTER;
import static org.apache.gobblin.source.extractor.extract.kafka.ConfigStoreUtils.GOBBLIN_CONFIG_TAGS_BLACKLIST;
import static org.apache.gobblin.source.extractor.extract.kafka.ConfigStoreUtils.GOBBLIN_CONFIG_TAGS_WHITELIST;
import static org.mockito.Matchers.anyList;


/**
 * Added this testing to protect no behavior changes on {@link ConfigStoreUtils} after refactoring.
 */
public class ConfigStoreUtilsTest {

  // Declare as string in convenience of testing.
  private String configStoreUri;

  private GobblinKafkaConsumerClient mockClient;

  private ConfigClient configClient = ConfigClient.createConfigClient(VersionStabilityPolicy.WEAK_LOCAL_STABILITY);

  /**
   * Loading a fs-based config-store for ease of unit testing.
   * @throws Exception
   */
  @BeforeClass
  public void setup()
      throws Exception {
    URL url = this.getClass().getClassLoader().getResource("_CONFIG_STORE");
    configStoreUri = getStoreURI(new Path(url.getPath()).getParent().toString()).toString();
    mockClient = Mockito.mock(GobblinKafkaConsumerClient.class);
  }

  @Test
  public void testGetTopicsFromConfigStore()
      throws Exception {
    KafkaTopic topic1 = new KafkaTopic("Topic1", Lists.newArrayList());
    KafkaTopic topic2 = new KafkaTopic("Topic2", Lists.newArrayList());
    KafkaTopic topic3 = new KafkaTopic("Topic3", Lists.newArrayList());

    Mockito.when(mockClient.getFilteredTopics(anyList(), anyList()))
        .thenReturn(ImmutableList.of(topic1, topic2, topic3));
    Properties properties = new Properties();

    // Empty properties returns everything: topic1, 2 and 3.
    List<KafkaTopic> result = ConfigStoreUtils.getTopicsFromConfigStore(properties, configStoreUri, mockClient);
    Assert.assertEquals(result.size(), 3);

    properties.setProperty(GOBBLIN_CONFIG_TAGS_WHITELIST, "/tags/whitelist");
    properties.setProperty(GOBBLIN_CONFIG_FILTER, "/data/tracking");
    properties.setProperty(GOBBLIN_CONFIG_COMMONPATH, "/data/tracking");

    // Whitelist only two topics. Should only returned whitelisted topics.
    result = ConfigStoreUtils.getTopicsFromConfigStore(properties, configStoreUri, mockClient);
    Assert.assertEquals(result.size(), 2);
    List<String> resultInString = result.stream().map(KafkaTopic::getName).collect(Collectors.toList());
    Assert.assertTrue(resultInString.contains("Topic1"));
    Assert.assertTrue(resultInString.contains("Topic2"));

    // Blacklist two topics. Should only return non-blacklisted topics.
    properties.remove(GOBBLIN_CONFIG_TAGS_WHITELIST);
    properties.setProperty(GOBBLIN_CONFIG_TAGS_BLACKLIST, "tags/blacklist");
    result = ConfigStoreUtils.getTopicsFromConfigStore(properties, configStoreUri, mockClient);
    Assert.assertEquals(result.size(), 1);
    Assert.assertEquals(result.get(0).getName(), "Topic3");
  }

  @Test
  public void testGetListOfTopicNamesByFilteringTag()
      throws Exception {
    Properties properties = new Properties();
    properties.setProperty(GOBBLIN_CONFIG_TAGS_WHITELIST, "/tags/whitelist");
    properties.setProperty(GOBBLIN_CONFIG_FILTER, "/data/tracking");
    properties.setProperty(GOBBLIN_CONFIG_COMMONPATH, "/data/tracking");

    List<String> result = ConfigStoreUtils
        .getListOfTopicNamesByFilteringTag(properties, configClient, Optional.absent(), configStoreUri,
            GOBBLIN_CONFIG_TAGS_WHITELIST);
    Assert.assertEquals(result.size(), 2);
    Assert.assertTrue(result.contains("Topic1"));
    Assert.assertTrue(result.contains("Topic2"));
  }

  /**
   * Return localFs-based config-store uri.
   * Note that for local FS, fs.getUri will return an URI without authority. So we shouldn't add authority when
   * we construct an URI for local-file backed config-store.
   */
  private URI getStoreURI(String configDir)
      throws URISyntaxException {
    return new URI("simple-file", "", configDir, null, null);
  }
}