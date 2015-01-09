/******************************************************************************* 
 * 
 * Copyright 2011 Zack Grossbart
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/
package com.grossbart.forbiddenfunction;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Level;

import com.google.common.collect.Lists;
import com.google.javascript.jscomp.ErrorManager;

import org.apache.commons.io.FileUtils;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.BooleanOptionHandler;

/**
 * The runner handles all argument processing and sets up the precompile process.  
 */
public class FFRunner
{
    
    @Option(name = "--help", handler = BooleanOptionHandler.class, usage = "Displays this message")
    private boolean m_displayHelp = false;
    
    /**
     * This enumeration handles all of the log level argument for the command line of 
     * this application.
     */
    private enum LogLevel {
        ALL {
            @Override
            public Level getLevel()
            {
                return Level.ALL;
            }
        },

        CONFIG {
            @Override
            public Level getLevel()
            {
                return Level.CONFIG;
            }
        },

        FINE {
            @Override
            public Level getLevel()
            {
                return Level.FINE;
            }
        },

        FINER {
            @Override
            public Level getLevel()
            {
                return Level.FINER;
            }
        },

        FINEST {
            @Override
            public Level getLevel()
            {
                return Level.FINEST;
            }
        },

        INFO {
            @Override
            public Level getLevel()
            {
                return Level.INFO;
            }
        },

        OFF {
            @Override
            public Level getLevel()
            {
                return Level.OFF;
            }
        },

        SEVERE {
            @Override
            public Level getLevel()
            {
                return Level.SEVERE;
            }
        },

        WARNING {
            @Override
            public Level getLevel()
            {
                return Level.WARNING;
            }
        };
        
        public abstract Level getLevel();
    }
    
    @Option(name = "--logging_level",
        usage = "The logging level (standard java.util.logging.Level values) for Compiler progress. " + 
            "Does not control errors or warnings for the JavaScript code under compilation")
    private LogLevel m_loggingLevel = LogLevel.WARNING;
    
    @Option(name = "--js_output_file",
        usage = "Primary output filename. If not specified, output is " +
        "written to stdout")
    private String m_output = null;
    
    @Option(name = "--js",
        usage = "The javascript filename. You may specify multiple")
    private List<String> m_js = Lists.newArrayList();
    
    @Option(name = "--lib_js", usage = "The javascript library filename. You may specify multiple", required = true)
    private List<String> m_libJs = Lists.newArrayList();
    
    @Option(name = "--externs", usage = "The file containing javascript externs. You may specify multiple")
    private List<String> m_externs = Lists.newArrayList();
    
    @Option(name = "--charset",
        usage = "Input and output charset for all files. By default, we " +
                "accept UTF-8 as input and output US_ASCII")
    private String m_charset = "UTF-8";
    
    @Option(name = "--print_tree",
        handler = BooleanOptionHandler.class,
        usage = "Prints out the parse tree and exits")
    private boolean m_printTree = false;
    
    @Option(name = "--skip_gzip",
        handler = BooleanOptionHandler.class,
        usage = "Skip GZIPing the results")
    private boolean m_skipGzip = false;
    
    @Option(name = "--no_validate",
        handler = BooleanOptionHandler.class,
        usage = "Pass this argument to skip the pre-parse file validation step.  This is faster, but won't " +
                "provide good error messages if the input files are invalid JavaScript.")
    private boolean m_preparse = true;
    
    @Option(name = "--separate_files",
        handler = BooleanOptionHandler.class,
        usage = "Pass this argument to separate library files and the regular files into different output files.  " + 
            "By default they are combined into a single file.")
    private boolean m_separate = false;
    
    @Option(name = "--flagfile",
        usage = "A file containing additional command-line options.")
    private String m_flagFile = "";
    
    private StringBuffer m_mainFiles = new StringBuffer();
    
    /**
     * Process the flags file and add the argument values to the current class.
     * 
     * @param out    the output stream for printing errors while parsing the arguments
     * 
     * @exception CmdLineException
     *                   if there's an error parsing the arguments
     * @exception IOException
     *                   if there is an exception reading the file
     */
    private void processFlagFile(PrintStream out)
        throws CmdLineException, IOException
    {
        if (m_flagFile == null || m_flagFile.trim().length() == 0) {
            return;
        }
        
        List<String> argsInFile = Lists.newArrayList();
        File flagFileInput = new File(m_flagFile);
        
        String flags = FileUtils.readFileToString(flagFileInput, m_charset);
        
        StringTokenizer tokenizer = new StringTokenizer(flags);
        
        while (tokenizer.hasMoreTokens()) {
            argsInFile.add(tokenizer.nextToken());
        }
        
        m_flagFile = "";
        
        CmdLineParser parserFileArgs = new CmdLineParser(this);
        parserFileArgs.parseArgument(argsInFile.toArray(new String[] {}));
        
        // Currently we are not supporting this (prevent direct/indirect loops)
        if (!m_flagFile.equals("")) {
            out.println("ERROR - Arguments in the file cannot contain --flagfile option.");
        }
    }
    
    /**
     * Add files for compilation.
     * 
     * @param slim   the compiler instance
     * @param files  the files to add
     * @param isLib  if these files are library files
     * 
     * @return true if the files were properly validated or false otherwise
     * @exception IOException
     *                   if there is an error reading the files
     */
    private boolean addFiles(ForbiddenFunction slim, List<String> files, boolean isLib)
        throws IOException
    {
        for (String file : files) {
            File f = new File(file);
            String contents = FileUtils.readFileToString(f, m_charset);
            
            if (m_preparse) {
                ErrorManager mgr = slim.validate(f.getAbsolutePath(), contents);
                if (mgr.getErrorCount() != 0) {
                    mgr.generateReport();
                    return false;
                }
            }
            
            if (!m_separate && !isLib) {
                m_mainFiles.append(contents + "\n");
            }
            
            if (isLib) {
                ForbiddenFunction.getLogger().log(Level.INFO, "Adding library file: " + f.getAbsoluteFile());
            } else {
                ForbiddenFunction.getLogger().log(Level.INFO, "Adding main file: " + f.getAbsoluteFile());
            }
            
            slim.addSourceFile(new JSFile(f.getName(), contents, isLib));
        }
        
        return true;
    }
    
    /**
     * Print the usage of this class.
     * 
     * @param parser the parser of the command line arguments
     */
    private static void printUsage(CmdLineParser parser)
    {
        System.out.println("java FFRunner [options...] arguments...\n");
        // print the list of available options
        parser.printUsage(System.out);
        System.out.println();
    }
    

    /**
     * The main entry point.
     * 
     * @param args   the arguments for this process
     */
    public static void main(String[] args)
    {
        FFRunner runner = new FFRunner();
        
        // parse the command line arguments and options
        CmdLineParser parser = new CmdLineParser(runner);
        parser.setUsageWidth(80); // width of the error display area
        
        if (args.length == 0) {
            printUsage(parser);
            return;
        }
        
        try {
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            System.out.println(e.getMessage() + '\n');
            printUsage(parser);
            return;
        }
        
        try {
            runner.processFlagFile(System.out);
            
            if (runner.m_displayHelp) {
                parser.printUsage(System.out);
                return;
            }
            
            /*
             * TODO call it
             */
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
