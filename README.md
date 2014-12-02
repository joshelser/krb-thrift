# Passing Kerberos credentials over Thrift RPC using Hadoop UserGroupInformation 

This is a quick example which can show a client's cached kerberos credentials can be passed
over a Thrift RPC to a server which can proxy that user on top of the server's credentials. Hadoop's
UserGroupInformation is used to ensure compatibility with all that is already provided to us from them.

Bits of code borrowed from Apache Accumulo, Apache Hadoop and Apache Hive. The Thrift client and server classes were based on http://stackoverflow.com/a/13839827/1580381.

The actual RPC performed is an `ls` on HDFS (e.g. `echo "/" | hdfs dfs -ls`). This helps us verify that we do have valid credentials on the server-side to perform the requested action (relying on HDFS to fail if we send it something malformed/incorrect).

# Building

A simple `mvn package` will build the project building a jar for the files in the project, as well as bundling the dependencies. Check the pom.xml file to see the current dependencies (of most important, they were originally Apache Thrift 0.9.1 and Apache Hadoop 2.6.0).

# Start Server

To run the Server, make sure you have Kerberos principal and credentials (typically a service user with a keytab as opposed to a password).

`java -cp "${HADOOP_CONF_DIR}:${HADOOP_HOME}/share/hadoop/common/*:${HADOOP_HOME}/share/hadoop/common/lib/*:${HADOOP_HOME}/share/hadoop/hdfs/*:${HADOOP_HOME}/share/hadoop/hdfs/lib/*:krb-thrift-1.0-SNAPSHOT-jar-with-dependencies.jar joshelser.Server -p service/host.example.com@EXAMPLE.COM`

Server options

```
Usage: joshelser.Server [options]
  Options:
  * -k, --keytab
       Kerberos keytab
        --port
       Port to bind the Thrift server on, default 7911
       Default: 7911
  * -p, --principal
       Kerberos principal for the provided keytab, _HOST expansion allowed.
```

# Invoke client

To run the Client, `kinit` and cache your Kerberos credentials (password and username is common here). The options you provide here (`-p` and `-i`) *must* match the principal (primary and instance, for options `p` and `i` respectively -- see http://web.mit.edu/kerberos/krb5-1.5/krb5-1.5.4/doc/krb5-user/What-is-a-Kerberos-Principal_003f.html for more details on components in a Kerberos principal). The `-s` option allows you to separate the host actually running the service and the instance (hostname) used in the Kerberos principal for the server.

`java -classpath /etc/hadoop/conf:/home/vagrant/krb-thrift-1.0-SNAPSHOT-jar-with-dependencies.jar  joshelser.Client -s host.example.com -p service -i host.example.com`

The client's cached Kerberos credentials will be automatically passed to the server and the server will perform the RPC action with the client's credentials proxied on top of its own.

Client options

```
Usage: joshelser.Client [options]
  Options:
    -d, --dir
       HDFS directory to perform `ls` on
       Default: /
  * -i, --instance
       Second component of the Kerberos principal for the server
        --port
       Port of the Thrift server, defaults to
       Default: 7911
  * -p, --primary
       Leading component of the Kerberos principal for the server
  * -s, --server
       Hostname of Thrift server
```

# Example

Client output:

```
$ java -classpath /etc/hadoop/conf:krb-thrift-1.0-SNAPSHOT-jar-with-dependencies.jar  joshelser.Client -s node1.example.com -p accumulo -i node1.example.com  -d /apps/accumulo
14/12/02 09:22:33 INFO joshelser.Client: Security is enabled: true
14/12/02 09:22:33 INFO joshelser.Client: Current user: jelser@EXAMPLE.COM (auth:KERBEROS)
$ ls /apps/accumulo
instance_id/
recovery/
tables/
version/
wal/
```

Server output:

```
2014-12-02 09:22:24,419 [security.UserGroupInformation] [main] INFO : Login successful for user accumulo/node1.example.com@EXAMPLE.COM using keytab file /etc/security/keytabs/accumulo.service.keytab
2014-12-02 09:22:24,421 [joshelser.Server] [main] INFO : Current user: accumulo/node1.example.com@EXAMPLE.COM (auth:KERBEROS)
2014-12-02 09:22:33,773 [joshelser.TUGIAssumingProcessor] [pool-4-thread-1] DEBUG: Executing action as jelser
2014-12-02 09:22:33,797 [joshelser.HdfsServiceImpl] [pool-4-thread-1] DEBUG: Running as jelser@EXAMPLE.COM (auth:PROXY) via accumulo/node1.example.com@EXAMPLE.COM (auth:KERBEROS)
2014-12-02 09:22:33,936 [joshelser.TUGIAssumingProcessor] [pool-4-thread-1] DEBUG: Executing action as jelser
```
