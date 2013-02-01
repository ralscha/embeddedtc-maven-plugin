# embeddedtc-maven-plugin

**embeddedtc-maven-plugin** is a [Maven](http://maven.apache.org/) plugin that bundles one or multiple war files and [Apache Tomcat 7](http://tomcat.apache.org/) into one _executable_ jar.
On the target machine a simple `java -jar myproject.jar` starts Tomcat and deploys the included war files. 

The official [Tomcat Maven plugin](http://tomcat.apache.org/maven-plugin-2.1-SNAPSHOT/) also provides an executable jar creator. 
The main difference from this plugin to the one from Apache is the runtime configuration.   
* The Apache plugin uses well known xml files (context.xml, server.xml) and command line parameters to control the runtime behaviour.
* This plugin uses one [yaml](http://en.wikipedia.org/wiki/YAML) configuration file that controls everything.   

If the Apache plugin fits your needs then there is no reason to switch to this plugin.    

## Quick Start

Add this plugin configuration to the plugins section in the pom.xml of your web application project. The packaging of the project should be _war_.
```
<build>
 <plugins>  
  ...
  <plugin>
    <groupId>ch.rasc</groupId>
    <artifactId>embeddedtc-maven-plugin</artifactId>
    <version>1.0.0</version>
    <executions>
      <execution>
        <goals>  
          <goal>package-tcwar</goal>
        </goals>
      </execution>
    </executions>
  </plugin>
  ...
 </plugins>
</build> 	
```

The plugins runs during the package phase. `mvn package` creates a war and in the same directory a jar file with
the name &lt;artifactId&gt;-&lt;version&gt;-embeddedtc.jar. 
The jar is about 2.7 MB bigger in size than the war file because of the embedded Tomcat classes. 

The command `java -jar thejarfile.jar` starts the the embedded Tomcat 7 on port 8080 and deploys the war to the ROOT (/) context.
 
 
## Plugin configuration

The plugin supports the following configuration options:

- **finalName**  
Specifies the file name of the executable jar. If this option is not present the name of jar is &lt;artifactId&gt;-&lt;version&gt;-embeddedtc.jar.
The following configuration creates a jar file with the name _my_all_in_one.jar_
```
  <plugin>
      <groupId>ch.rasc</groupId>
      <artifactId>embeddedtc-maven-plugin</artifactId>
      <version>1.0.0</version>
      <executions>
        <execution>
          <goals>  
            <goal>package-tcwar</goal>
          </goals>
        </execution>
      </executions>
      <configuration>
        <finalName>my_all_in_one.jar</finalName>
      </configuration>
  </plugin>
```


- **includeJSPSupport**  
Specifies if the plugin includes JSP libraries into the jar file. This option is true by default. Excluding JSP support reduces the jar file size
by about 800 KB.
``` 
  <configuration>
      <includeJSPSupport>false</includeJSPSupport>
  </configuration>
```


- **extraDependencies**  
Additional dependencies that needs to be present on the Tomcat classpath are added with this configuration option. 
Usually these are jdbc driver libraries if Tomcat's jdbc connection pool is used. These artefacts will be bundled into the executable jar. 
```
  <extraDependencies>
      <dependency>
        <groupId>mysql</groupId>
        <artifactId>mysql-connector-java</artifactId>
        <version>5.1.22</version>
      </dependency>
  </extraDependencies>
```  


- **extraWars**  
The executable jar supports more than one war file. With this option additional war files can be added to the jar file. 
```
  <extraWars>
      <dependency>
        <groupId>ch.rasc</groupId>
        <artifactId>svg2img</artifactId>
        <version>0.0.1</version>
        <type>war</type>
      </dependency>
  </extraWars>	
```


- **extraResources**  
Additional resources (e.g. native libraries/programs, documents, ...) can be bundled with this option. During startup these resources will be extracted into
a directory and a system property (EXTRA_RESOURCES_DIR) is set that contains the absolute path to this directory. 
```
  <extraResources>
      <resource>
        <directory>e:\native</directory>
        <includes>
          <include>*.exe</include>
        </includes>
        <excludes>
          <exclude>*.dll</exclude>
        </excludes>						     
      </resource>
  </extraResources>
```


- **includeTcNativeWin32** and **includeTcNativeWin64**  
These two configuration options specify a path to the tcnative dll for 32- and 64-bit Windows systems. If the executable jar is started on a Windows computer and the
jar contains these dlls the runner extracts the dll that matches the os architecture and sets the library path to the directory where the dll resides. 
The embedded Tomcat is then able to use APR/native connectors and other APR features.
```
  <includeTcNativeWin32>E:\tomcat-native-1.1.24-win32-bin\bin\tcnative-1.dll</includeTcNativeWin32>
  <includeTcNativeWin64>E:\tomcat-native-1.1.24-win32-bin\bin\x64\tcnative-1.dll</includeTcNativeWin64>
```


## Runtime configuration
_TODO_

## Check configuration
_TODO_

## Obfuscate passwords
_TODO_


_____

## Changelog

### 1.0.0     February ?, 2013  
  * Initial release with Tomcat 7.0.35
