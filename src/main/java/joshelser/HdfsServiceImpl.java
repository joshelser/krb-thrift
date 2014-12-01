/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package joshelser;

import java.io.FileNotFoundException;
import java.io.IOException;

import joshelser.thrift.HdfsService;

import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class HdfsServiceImpl implements HdfsService.Iface {
  private static final Logger log = LoggerFactory.getLogger(HdfsServiceImpl.class);
  private FileSystem fs;

  public HdfsServiceImpl(FileSystem fs) {
    this.fs = fs;
  }

  @Override
  public String ls(String directory) throws TException {
    StringBuilder sb = new StringBuilder(64);
    try {
      log.debug("Running as {}", UserGroupInformation.getCurrentUser());
      for (FileStatus stat : fs.listStatus(new Path(directory))) {
        sb.append(stat.getPath().getName());
        if (stat.isDirectory()) {
          sb.append("/");
        }
        sb.append("\n");
      }
    } catch (FileNotFoundException e) {
      System.err.println("Got FileNotFoundException");
      e.printStackTrace(System.err);
      throw new TException(e);
    } catch (IllegalArgumentException e) {
      System.err.println("Got IllegalArgumentException");
      e.printStackTrace(System.err);
      throw new TException(e);
    } catch (IOException e) {
      System.err.println("Got IOException");
      e.printStackTrace(System.err);
      throw new TException(e);
    }

    return sb.toString();
  }

}
