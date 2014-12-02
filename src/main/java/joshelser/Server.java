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

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

import javax.security.sasl.Sasl;

import joshelser.thrift.HdfsService;
import joshelser.thrift.HdfsService.Iface;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.security.HadoopKerberosName;
import org.apache.hadoop.security.SaslRpcServer;
import org.apache.hadoop.security.SecurityUtil;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TSaslServerTransport;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TTransportFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

/**
 *
 */
public class Server {
  private static final Logger log = LoggerFactory.getLogger(Server.class);

  private static class Opts {
    @Parameter(names = {"-k", "--keytab"}, required = true, description = "Kerberos keytab")
    private String keytab;

    @Parameter(names = {"-p", "--principal"}, required = true, description = "Kerberos principal for the provided keytab, _HOST expansion allowed.")
    private String principal;
  }

  public static void main(String[] args) throws Exception {
    Opts opts = new Opts();

    JCommander commander = new JCommander();
    commander.addObject(opts);
    commander.setProgramName(Client.class.getName());
    try {
      commander.parse(args);
    } catch (ParameterException ex) {
      commander.usage();
      System.err.println(ex.getMessage());
      System.exit(1);
    }

    Configuration conf = new Configuration();
    FileSystem fs = FileSystem.get(conf);

    String principal = SecurityUtil.getServerPrincipal(opts.principal, InetAddress.getLocalHost().getCanonicalHostName());
    HadoopKerberosName name = new HadoopKerberosName(principal);
    String primary = name.getServiceName();
    String instance = name.getHostName();

    UserGroupInformation.loginUserFromKeytab(principal, opts.keytab);

    UserGroupInformation serverUser = UserGroupInformation.getLoginUser();
    log.info("Current user: {}", serverUser);

    TServerSocket serverTransport = new TServerSocket(7911);  // new server on port 7911
    HdfsService.Processor<Iface> processor = new HdfsService.Processor<Iface>(new HdfsServiceImpl(fs)); // Wrap the thrift server impl
    Map<String,String> saslProperties = new HashMap<String,String>();
    saslProperties.put(Sasl.QOP, "auth-conf");  // authorization and confidentiality

    TSaslServerTransport.Factory saslTransportFactory = new TSaslServerTransport.Factory();     // Creating the server definition
    saslTransportFactory.addServerDefinition(
                "GSSAPI",       //  tell SASL to use GSSAPI, which supports Kerberos
                primary,   //  base kerberos principal name - myprincipal/my.server.com@MY.REALM
                instance,    //  kerberos principal server - myprincipal/my.server.com@MY.REALM
                saslProperties,      //  Properties set, above
                new SaslRpcServer.SaslGssCallbackHandler()); // Ensures that authenticated user is the same as the authorized user

    TTransportFactory ugiTransportFactory = new TUGIAssumingTransportFactory(saslTransportFactory, serverUser);
    TUGIAssumingProcessor ugiProcessor = new TUGIAssumingProcessor(processor, true);
    TServer server = new TThreadPoolServer(new TThreadPoolServer.Args(serverTransport).transportFactory(ugiTransportFactory).processor(ugiProcessor));

    server.serve();   // Thrift server start
  }
}
