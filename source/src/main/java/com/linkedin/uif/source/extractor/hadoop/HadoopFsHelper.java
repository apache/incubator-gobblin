package com.linkedin.uif.source.extractor.hadoop;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.apache.avro.Schema;
import org.apache.avro.file.DataFileReader;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.mapred.FsInput;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linkedin.uif.configuration.ConfigurationKeys;
import com.linkedin.uif.configuration.State;
import com.linkedin.uif.source.extractor.filebased.FileBasedHelper;
import com.linkedin.uif.source.extractor.filebased.FileBasedHelperException;

public class HadoopFsHelper implements FileBasedHelper
{   
    private static Logger log = LoggerFactory.getLogger(HadoopFsHelper.class);
    private State state;
    private FileSystem fs;
    
    public HadoopFsHelper(State state) {
        this.state = state;
    }
    
    public FileSystem getFileSystem() {
        return this.fs;
    }
    
    @Override
    public void connect() throws FileBasedHelperException
    {
        URI uri = null;
        try {
            uri = new URI(state.getProp(ConfigurationKeys.SOURCE_FILEBASED_FS_URI));
            this.fs = FileSystem.get(uri, new Configuration());
        } catch (IOException e) {
            throw new FileBasedHelperException("Cannot connect to given URI " + uri + " due to "  + e.getMessage(), e);
        } catch (URISyntaxException e) {
            throw new FileBasedHelperException("Malformed uri " + uri + " due to "  + e.getMessage(), e);
        }
    }

    @Override
    public void close() throws FileBasedHelperException
    {
        try {
            this.fs.close();
        } catch (IOException e) {
            throw new FileBasedHelperException("Cannot close Hadoop filesystem due to "  + e.getMessage(), e);
        }
    }

    @Override
    public List<String> ls(String path) throws FileBasedHelperException
    {
        List<String> results = new ArrayList<String>();
        try {
            for (FileStatus status : this.fs.listStatus(new Path(path))) {
                results.add(status.getPath().getName());
            }
        } catch (IOException e) {
            throw new FileBasedHelperException("Cannot do ls on path " + path + " due to "  + e.getMessage(), e);
        }
        return results;
    }

    @Override
    public InputStream getFileStream(String path) throws FileBasedHelperException
    {
        try {
            return this.fs.open(new Path(path));
        } catch (IOException e) {
            throw new FileBasedHelperException("Cannot do open file " + path + " due to "  + e.getMessage(), e);
        }
    }
    
    public Schema getAvroSchema(String file) throws FileBasedHelperException {
        DataFileReader<GenericRecord> dfr = null;
        try {
            dfr = new DataFileReader<GenericRecord>(new FsInput(new Path(file), fs.getConf()), new GenericDatumReader<GenericRecord>());
            return dfr.getSchema();
        } catch (IOException e) {
            throw new FileBasedHelperException("Failed to open avro file " + file + " due to error " + e.getMessage(), e);
        } finally {
            if (dfr != null) {
                try {
                    dfr.close();
                } catch (IOException e) {
                    log.error("Failed to close avro file " + file, e);
                }
            }
        }
    }
    
    public <D> DataFileReader<D> getAvroFile(String file) throws FileBasedHelperException {
        try {
            return new DataFileReader<D>(new FsInput(new Path(file), fs.getConf()), new GenericDatumReader<D>());
        } catch (IOException e) {
            throw new FileBasedHelperException("Failed to open avro file " + file + " due to error " + e.getMessage(), e);
        }
    }
}
