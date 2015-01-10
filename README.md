Forbidden Functions
==================================================

This is a JavaScript static analyzer which finds forbidden functions.  If you want make sure that your code never calls eval, or jQuery, or anything else this tool will automatically find function calls you don't want to allow.

Building Forbidden Functions
--------------------------------------

This project builds with [Gradle](http://www.gradle.org).  Build the application by running gradle in the project root directory.

This project builds and runs on Windows, Mac, and Linux.

Using Forbidden Functions
--------------------------------------

<pre><code>
FFRunner [options...] arguments...

 --charset VAL                          : Input and output charset for all
                                          files. By default, we accept UTF-8 as
                                          input and output US_ASCII
 --flagfile VAL                         : A file containing additional
                                          command-line options.
 --help                                 : Displays this message
 --logging_level [ALL | CONFIG | FINE   : The logging level (standard
 | FINER | FINEST | INFO | OFF |          java.util.logging.Level values) for
 SEVERE | WARNING]                        Compiler progress. Does not control
                                          errors or warnings for the JavaScript
                                          code under compilation
 --no_validate                          : Pass this argument to skip the
                                          pre-parse file validation step.  This
                                          is faster, but won't provide good
                                          error messages if the input files are
                                          invalid JavaScript.
 --print_tree                           : Prints out the parse tree and exits
 -funcs VAL                             : The file containing list of forbidden
                                          function names. You may specify
                                          multiple
</code></pre>

You can also call this as an API as part of a build.  To use this in a gradle build you would do something like this:

<pre><code>
buildscript {
  repositories {
    mavenCentral()
  }
  dependencies {
    classpath 'com.grossbart:forbidden_function:0.1'
  }
}

task checkForbiddenFunctions(dependsOn: 'classes') {
    description 'This task checks JavaScript files for forbidden functions'
    doFirst() {
        println ':checkForbiddenFunctions'
    
        def path = project.projectDir
        def args = []
        args.add('-funcs')
        args.add(new File(project.projectDir, 'src/build/forbidden_functions.txt'))
        
        FileTree sourceFiles = fileTree(dir: 'src/main/js/')
        sourceFiles.include '*.js'
        
        sourceFiles.each { f ->
            args.add(f)
        }
        
        def errors = com.grossbart.forbiddenfunction.FFRunner.run(args as String[])
        
        if (errors.size > 0) {
            errors.each { err -> 
                println err
            }
            throw new GradleScriptException("There were forbidden functions.  Look above and fix the issues.", null);
        }
    }
}
</code></pre>

I might turn this into a first class Gradle plugin later.

Creating the Forbidden File
--------------------------------------

The forbidden functions list contains all of the functions you don't want to allow in your project.  There's one entry per line.  For example, if you want to dissallow jQuery in your project you would have a forbidden functions file like this:

<pre><code>$
jQuery</code></pre>

Checking for Forbidden Functions
--------------------------------------

Once you've built the project you're ready to check for forbidden functions.  There's a sample file in the project that calls jQuery in two different ways.  We can check for this functions with this command:

<pre><code>build/install/forbiddenfunction/bin/forbiddenfunction -funcs forbidden.txt main.js</code></pre>

Forbidden functions will show you the functions you've forbidden like this:

<pre><code>There were forbidden functions.
/Users/zackgrossbart/priv_work/forbiddenfunctions/main.js: line 4, calling forbidden function $
/Users/zackgrossbart/priv_work/forbiddenfunctions/main.js: line 4, calling forbidden function $
/Users/zackgrossbart/priv_work/forbiddenfunctions/main.js: line 9, calling forbidden function jQuery
/Users/zackgrossbart/priv_work/forbiddenfunctions/main.js: line 9, calling forbidden function jQuery
/Users/zackgrossbart/priv_work/forbiddenfunctions/main.js: line 15, calling forbidden function $
/Users/zackgrossbart/priv_work/forbiddenfunctions/main.js: line 15, calling forbidden function $</code></pre>