## Overview

The `barrister-maven-plugin` allows you to convert Barrister RPC IDL files to .java files with
a minimum amount of configuration.

Add to your `pom.xml`:

```xml
  <build>
      <plugins>
          <plugin>
              <!-- These three properties are boilerplate -->
              <groupId>com.bitmechanic</groupId>
              <artifactId>barrister-maven-plugin</artifactId>
              <version>1.0-SNAPSHOT</version>

              <!-- Your configuration options here -->
              <configuration>
                  <!-- uncomment below to use the command line barrister translator -->
                  <!--<barristerScript>/usr/bin/barrister</barristerScript>-->

                  <!-- this uses the hosted translator -->
                  <barristerScript>http://barrister.bitmechanic.com/run</barristerScript>

                  <idlFiles>src/main/resources/idl</idlFiles>
                  <exclude>conform-inc.idl</exclude>
                  <basePackage>com.bitmechanic.test</basePackage>
                  <outputDirectory>src/main/java</outputDirectory>
                  <immutable>true</immutable>
                  <clean>true</clean>
              </configuration>

              <!-- More boilerplate -->
              <executions>
                  <execution>
                      <phase>generate-sources</phase>
                      <goals>
                          <goal>idl2java</goal>
                      </goals>
                  </execution>
              </executions>
          </plugin>
      </plugins>
  </build>
```

## Configuration Reference

The following config options are available:

~~~
Element             | Default                        | Notes
`<barristerScript>` | barrister                      | Either a path to the Python Barrister translation script, or the URL to the hosted service
`<idlFiles>`        | src/main/resources/barrister   | Comma separated paths to IDL files. Directories will have all *.idl files included.
`<exclude>`         | (none)                         | Comma separated file names to exclude from translation. Wild cards are not currently supported.
`<basePackage>`     | $groupId.$artifactId.generated | Base Java package for generated .java files - each IDL file will be a sub-package from this root
`<outputDirectory>` | src/main/java                  | Base output directory to generate source code to into
`<immutable>`       | false                          | If true, generated Java struct classes will be immutable (no setters, arrays are cloned on read)
`<clean>`           | false                          | If true, the outputDirectory+basePackage will be recursively deleted before translation to remove artifacts from previous invocation
~~~

Once you have your project configured, I highly recommend adding `<clean>true</clean>` to ensure you don't have orphaned
.java files from previous runs on disk.  It is disabled by default simply to prevent you from accidentally wiping out your
source tree.

## See also

* http://barrister.bitmechanic.com/ - the main Barrister RPC site
* https://github.com/coopernurse/barrister-java - docs on the Java bindings