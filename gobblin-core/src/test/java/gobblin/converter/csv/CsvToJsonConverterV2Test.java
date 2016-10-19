package gobblin.converter.csv;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.opencsv.CSVParser;

@Test(groups = {"gobblin.converter"})
public class CsvToJsonConverterV2Test {
  private String row11Cols = "20160924,desktop,Dynamic Segment,42935,0.0446255968324211,1590.4702457202748,348380,8.1141260044252945,232467,206.98603475430664,33028";
  private String row10Cols = "20160924,desktop,42935,0.0446255968324211,1590.4702457202748,348380,8.1141260044252945,232467,206.98603475430664,33028";

  public void convertOutput() throws IOException {
    JsonParser parser = new JsonParser();
    JsonElement jsonElement = parser.parse(new InputStreamReader(getClass().getResourceAsStream("/converter/csv/schema_with_10_fields.json")));

    JsonArray outputSchema = jsonElement.getAsJsonArray();
    CSVParser csvParser = new CSVParser();
    String[] inputRecord = csvParser.parseLine(row10Cols);

    CsvToJsonConverterV2 converter = new CsvToJsonConverterV2();
    JsonObject actual = converter.createOutput(outputSchema, inputRecord);
    JsonObject expected = parser.parse(new InputStreamReader(getClass().getResourceAsStream("/converter/csv/10_fields.json")))
                                .getAsJsonObject();

    Assert.assertEquals(expected, actual);
  }

  public void convertOutputSkippingField() throws IOException {
    JsonParser parser = new JsonParser();
    JsonElement jsonElement = parser.parse(new InputStreamReader(getClass().getResourceAsStream("/converter/csv/schema_with_10_fields.json")));

    JsonArray outputSchema = jsonElement.getAsJsonArray();
    CSVParser csvParser = new CSVParser();
    String[] inputRecord = csvParser.parseLine(row11Cols);

    CsvToJsonConverterV2 converter = new CsvToJsonConverterV2();
    String[] customOrder = {"0","1","3","4","5","6","7","8","9","10"};
    JsonObject actual = converter.createOutput(outputSchema, inputRecord, Arrays.asList(customOrder));
    JsonObject expected = parser.parse(new InputStreamReader(getClass().getResourceAsStream("/converter/csv/10_fields.json")))
                                .getAsJsonObject();

    Assert.assertEquals(expected, actual);
  }

  public void convertOutputMismatchFields() throws IOException {
    JsonParser parser = new JsonParser();
    JsonElement jsonElement = parser.parse(new InputStreamReader(getClass().getResourceAsStream("/converter/csv/schema_with_10_fields.json")));

    JsonArray outputSchema = jsonElement.getAsJsonArray();
    CSVParser csvParser = new CSVParser();
    String[] inputRecord = csvParser.parseLine(row11Cols);

    CsvToJsonConverterV2 converter = new CsvToJsonConverterV2();
    try {
      converter.createOutput(outputSchema, inputRecord);
      Assert.fail();
    } catch (Exception e) {

    }
  }

  public void convertOutputAddingNull() throws IOException {
    JsonParser parser = new JsonParser();
    JsonElement jsonElement = parser.parse(new InputStreamReader(getClass().getResourceAsStream("/converter/csv/schema_with_11_fields.json")));

    JsonArray outputSchema = jsonElement.getAsJsonArray();
    CSVParser csvParser = new CSVParser();
    String[] inputRecord = csvParser.parseLine(row11Cols);

    CsvToJsonConverterV2 converter = new CsvToJsonConverterV2();
    String[] customOrder = {"0","1","-1","3","4","5","6","7","8","9","10"};
    JsonObject actual = converter.createOutput(outputSchema, inputRecord, Arrays.asList(customOrder));
    JsonObject expected = parser.parse(new InputStreamReader(getClass().getResourceAsStream("/converter/csv/11_fields_with_null.json")))
                                .getAsJsonObject();
    Assert.assertEquals(expected, actual);
  }
}
