package com.linkedin.uif.converter;

import com.linkedin.uif.configuration.WorkUnitState;

public interface Converter<SI, SO, DI, DO>
{

  public SO convertSchema(SI inputSchema, WorkUnitState workUnit) throws SchemaConversionException;

  public DO convertRecord(SO outputSchema, DI inputRecord, WorkUnitState workUnit) throws DataConversionException;

}
