package com.linkedin.uif.source.extractor.extract;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.linkedin.uif.source.extractor.watermark.Predicate;
import com.linkedin.uif.source.extractor.DataRecordException;
import com.linkedin.uif.source.extractor.exception.HighWatermarkException;
import com.linkedin.uif.source.extractor.exception.RecordCountException;
import com.linkedin.uif.source.extractor.exception.SchemaException;
import com.linkedin.uif.source.workunit.WorkUnit;

/**
 * An interface for source extractors
 *
 * @param <D> type of data record
 * @param <S> type of schema
 */
public interface SourceSpecificLayer<S, D> {
    /**
     * Metadata to extract raw schema(like url, query)
     *
     * @param source schema name
     * @param source entity name
     * @return list of commands to get schema
     * @throws SchemaException if there is anything wrong in building metadata for schema extraction
     */
	public List<Command> getSchemaMetadata(String schema, String entity) throws SchemaException;
	
    /**
     * Raw schema from the response
     *
     * @param response is the output from a source call
     * @return S representation of the schema
     * @throws SchemaException if there is anything wrong in getting raw schema
     */
	public S getSchema(CommandOutput<?, ?> response) throws SchemaException, IOException;
	
    /**
     * Metadata for high watermark(like url, query)
     *
     * @param source schema name
     * @param source entity name
     * @param water mark column
     * @param lis of all predicates that needs to be applied
     * @return list of commands to get the high watermark
     * @throws HighWatermarkException if there is anything wrong in building metadata to get high watermark
     */
	public List<Command> getHighWatermarkMetadata(String schema, String entity, String watermarkColumn, List<Predicate> predicateList) throws HighWatermarkException;
	
    /**
     * High watermark from the response
     *
     * @param source schema name
     * @param source entity name
     * @param water mark column
     * @param lis of all predicates that needs to be applied
     * @return high water mark from source
     * @throws HighWatermarkException if there is anything wrong in building metadata to get high watermark
     */
	public long getHighWatermark(CommandOutput<?, ?> response, String watermarkColumn, String predicateColumnFormat) throws HighWatermarkException;
	
    /**
     * Metadata for record count(like url, query)
     *
     * @param source schema name
     * @param source entity name
     * @param work unit: properties
     * @param lis of all predicates that needs to be applied
     * @return list of commands to get the count
     * @throws RecordCountException if there is anything wrong in building metadata for record counts
     */
	public List<Command> getCountMetadata(String schema, String entity, WorkUnit workUnit, List<Predicate> predicateList) throws RecordCountException;
	
    /**
     * Record count from the response
     *
     * @return record count
     * @throws RecordCountException if there is anything wrong in getting record count
     */
	public long getCount(CommandOutput<?, ?> response) throws RecordCountException;
	
    /**
     * Metadata for data records(like url, query)
     *
     * @param source schema name
     * @param source entity name
     * @param work unit: properties
     * @param list of all predicates that needs to be applied
     * @return list of commands to get the data
     * @throws DataRecordException if there is anything wrong in building metadata for data records
     */
	public List<Command> getDataMetadata(String schema, String entity, WorkUnit workUnit, List<Predicate> predicateList) throws DataRecordException;
	
    /**
     * Set of data records from the response
     *
     * @return Iterator over objects of type D
     * @throws DataRecordException if there is anything wrong in getting data records
     */
	public Iterator<D> getData(CommandOutput<?, ?> response) throws DataRecordException, IOException;
	
    /**
     * Data type of source
     *
     * @return Map of source and target data types
     */
	public Map<String, String> getDataTypeMap();

    /**
     * Get records using source specific api (Example: bulk api in salesforce source)
     * record set: data records with an iterator
     *
     * @param source schema name
     * @param source entity name
     * @param work unit: properties
     * @param list of all predicates that needs to be applied
     * @return iterator with set of records
     * @throws SchemaException if there is anything wrong in getting data records
     */
	public Iterator<D> getRecordSetFromSourceApi(String schema, String entity, WorkUnit workUnit, List<Predicate> predicateList) throws IOException;
}
