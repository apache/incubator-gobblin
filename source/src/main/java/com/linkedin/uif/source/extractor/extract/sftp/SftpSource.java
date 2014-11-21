/* (c) 2014 LinkedIn Corp. All rights reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at  http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied.
 */

 package com.linkedin.uif.source.extractor.extract.sftp;

import java.io.IOException;

import com.linkedin.uif.configuration.State;
import com.linkedin.uif.configuration.WorkUnitState;
import com.linkedin.uif.source.extractor.Extractor;
import com.linkedin.uif.source.extractor.filebased.FileBasedHelperException;
import com.linkedin.uif.source.extractor.filebased.FileBasedSource;

public class SftpSource<S, D> extends FileBasedSource<S, D>
{
    @Override
    public Extractor<S, D> getExtractor(WorkUnitState state) throws IOException
    {
        return new SftpExtractor<S, D>(state);
    }

    @Override
    public void initFileSystemHelper(State state) throws FileBasedHelperException
    {
        this.fsHelper = new SftpFsHelper(state);
        this.fsHelper.connect();
    }
}
