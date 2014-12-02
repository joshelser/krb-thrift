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

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

/**
 *
 */
public abstract class ParseBase {

  /**
   * Parses the arguments using JCommander and the annotated {@link options} instance.
   *
   * Exits the program if the options fail to parse.
   *
   * @param args
   *          Command line arguments
   * @param options
   *          JCommander-annotated class instance
   */
  public void parseArgs(Class<?> clz, String[] args) {
    JCommander commander = new JCommander();
    commander.addObject(this);
    commander.setProgramName(clz.getName());
    try {
      commander.parse(args);
    } catch (ParameterException ex) {
      commander.usage();
      System.err.println(ex.getMessage());
      System.exit(1);
    }
  }
}
