package gobblin.source.extractor.extract.kafka;

import gobblin.configuration.WorkUnitState;
import gobblin.source.extractor.Extractor;
import kafka.message.MessageAndOffset;

import java.io.IOException;

/**
 * An implementation of {@link KafkaExtractor} from which reads and returns records as an array of bytes.
 *
 * @author akshay@nerdwallet.com
 */
public class KafkaSimpleExtractor extends KafkaExtractor<String, byte[]> {

  public KafkaSimpleExtractor(WorkUnitState state) {
    super(state);
  }
  @Override
  protected byte[] decodeRecord(MessageAndOffset messageAndOffset, byte[] reuse) throws SchemaNotFoundException, IOException {
    return getBytes(messageAndOffset.message().payload());
  }

  /**
   * Get the schema (metadata) of the extracted data records.
   *
   * @return the Kafka topic being extracted
   * @throws IOException if there is problem getting the schema
   */
  @Override
  public String getSchema() throws IOException {
    return this.partition.getTopicName();
  }
}
