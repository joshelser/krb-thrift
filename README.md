A quick example which can show a client's cached kerberos credentials can be passed
over a Thrift RPC to a server which can proxy that user on top of the server's credentials.

Bits of code borrowed from Accumulo, Hadoop and Hive.

Client should have a their Kerberos credentials already cached. Server expects
to receive two command line arguments: the principal and a keytab. Code in Server.java
and Client.java are still both hardcoded for "accumulo" as the principal and "node1.example.com" as the host.
Will fix those up later.
