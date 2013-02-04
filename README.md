# embeddedtc-maven-plugin

**embeddedtc-maven-plugin** is a [Maven](http://maven.apache.org/) plugin that bundles one or multiple war files and [Apache Tomcat 7](http://tomcat.apache.org/) into one _executable_ jar.
On the target machine a simple `java -jar myproject.jar` starts Tomcat and deploys the included war files. 

The official [Tomcat Maven plugin](http://tomcat.apache.org/maven-plugin-2.1-SNAPSHOT/) also provides an executable jar creator. 
The main difference from this plugin to the one from Apache is the runtime configuration.   
* The Apache plugin uses xml files (context.xml, server.xml) and command line parameters to control the runtime behavior.
* This plugin uses one [yaml](http://en.wikipedia.org/wiki/YAML) configuration file that controls everything. Although it also supports a /META-INF/context.xml file embedded inside the war.    

If the Apache plugin fits your needs then there is no reason to switch to this plugin.    


### See [Wiki](https://github.com/ralscha/embeddedtc-maven-plugin/wiki/Quick-Start) for more information.
