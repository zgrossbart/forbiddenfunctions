/******************************************************************************* 
 * 
 * Copyright 2015 Zack Grossbart 
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

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.ErrorManager;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/**
 * ForbiddenFunction is a static code analysis tool for JavaScript.  It looks at a JavaScript program
 * and a set of library files it uses and creates a new library with only the functions
 * which are actually used.
 */
public class ForbiddenFunction 
{
    private static final Logger LOGGER = Logger.getLogger(ForbiddenFunction.class.getName());
    
    static {
        LOGGER.setUseParentHandlers(false);
        LOGGER.addHandler(new SlimConsoleHandler());
    }
    
    /**
     * Set the logging level for this process.  Most messages only show up with INFO logging.
     * 
     * @param level  the log level
     */
    public static void setLoggingLevel(Level level)
    {
        LOGGER.setLevel(level);
    }
    
    /**
     * Get the logger for this compile process.
     * 
     * @return the logger
     */
    public static Logger getLogger()
    {
        return LOGGER;
    }
    
    private List<Node> m_vars = new ArrayList<Node>();
    private List<Call> m_calls = new ArrayList<Call>();
    private List<String> m_forbiddenFunc = new ArrayList<String>();
    private List<String> m_errors = new ArrayList<String>();
    
    private List<JSFile> m_files = new ArrayList<JSFile>();
    
    private ErrorManager m_errMgr;
    
    private String m_charset = "UTF-8";
    private boolean m_printTree = false;
    
    /**
     * Add a source file for compilation.
     * 
     * @param file   the file to compile
     */
    public void addSourceFile(JSFile file)
    {
        m_files.add(file);
    }

    /**
     * Add a forbidden functio name
     * 
     * @param functionName the forbidden function name
     */
    public void addForbiddenFuncion(String functionName) 
    {
        m_forbiddenFunc.add(functionName);
    }
    
    /**
     * Validate the specified JavaScript file
     * 
     * @param name    the name of the file
     * @param content the file contents
     * 
     * @return the error manager containing any errors from the specified file
     */
    public static ErrorManager validate(String name, String content)
    {
        Compiler compiler = new Compiler();

        CompilerOptions options = new CompilerOptions();
        // Advanced mode is used here, but additional options could be set, too.
        CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(options);

        // To get the complete set of externs, the logic in
        // CompilerRunner.getDefaultExterns() should be used here.
        List<SourceFile> extern = new ArrayList<SourceFile>();
        extern.add(SourceFile.fromCode("externs.js", ""));

        // The dummy input name "input.js" is used here so that any warnings or
        // errors will cite line numbers in terms of input.js.
        List<SourceFile> input = new ArrayList<SourceFile>();
        input.add(SourceFile.fromCode(name, content));
        
        compiler.init(extern, input, options);

        compiler.parse();
        return compiler.getErrorManager();
    }

    /**
     * Check all of the files which have been passed to the compiler
     * 
     * @return a list containing errors about any forbidden functions
     *  
     */
    public List<String> check()
    {
        StringBuffer sb = new StringBuffer();
        
        for (JSFile file : m_files) {
            check(file.getName(), file.getContent());
        }

        return m_errors;
    }
    
    /**
     * Parse, compile, and slim the specified code
     * 
     * @param name      the name of the file to slim
     * @param code      JavaScript source code to compile.
     */
    private void check(String name, String code)
    {
        Compiler compiler = new Compiler();

        CompilerOptions options = new CompilerOptions();
        
        // To get the complete set of externs, the logic in
        // CompilerRunner.getDefaultExterns() should be used here.
        List<SourceFile> extern = new ArrayList<SourceFile>();
        extern.add(SourceFile.fromCode("externs.js", ""));

        // The dummy input name "input.js" is used here so that any warnings or
        // errors will cite line numbers in terms of input.js.
        List<SourceFile> input = new ArrayList<SourceFile>();
        input.add(SourceFile.fromCode(name, code));
        
        compiler.init(extern, input, options);

        compiler.parse();
        m_errMgr = compiler.getErrorManager();
        
        if (m_errMgr.getErrorCount() > 0) {
            /*
             Then there were errors parsing the file and we can't
             prune anything. 
             */
            return;
        }

        Node node = compiler.getRoot();
        if (m_printTree) {
            System.out.println("Tree before pruning:");
            System.out.println(node.toStringTree());
        }
        
        //System.out.println("node before change: " + compiler.toSource());
        
        LOGGER.log(Level.INFO, "starting process...");
        Node n = process(node);
        
        LOGGER.log(Level.INFO, "Done processing...");
        LOGGER.log(Level.FINE, "m_calls: " + m_calls);
        
        if (m_printTree) {
            System.out.println("Tree after pruning:");
            System.out.println(node.toStringTree());
        }
    }
    
    /**
     * Process this particular node looking for calls, interesting functions, and 
     * variables.
     * 
     * @param node   the node to process
     * 
     * @return the original node reference
     */
    private Node process(Node node)
    {
        Iterator<Node> nodes = node.children().iterator();
        
        while (nodes.hasNext()) {
            Node n = nodes.next();
            
            if (n.getType() == Token.VAR && n.getFirstChild().getType() == Token.NAME) {
                m_vars.add(n);
            } else if (n.getType() == Token.CALL || n.getType() == Token.NEW) {
                addCalls(n);
            } else if (n.getType() == Token.ASSIGN ||
                       n.getType() == Token.ASSIGN_BITOR  ||
                       n.getType() == Token.ASSIGN_BITXOR ||
                       n.getType() == Token.ASSIGN_BITAND ||
                       n.getType() == Token.ASSIGN_LSH ||
                       n.getType() == Token.ASSIGN_RSH ||
                       n.getType() == Token.ASSIGN_URSH ||
                       n.getType() == Token.ASSIGN_ADD ||
                       n.getType() == Token.ASSIGN_SUB ||
                       n.getType() == Token.ASSIGN_MUL ||
                       n.getType() == Token.ASSIGN_DIV ||
                       n.getType() == Token.ASSIGN_MOD) {
                /*
                 This is an assignment operator.  
                 */
                addAssign(n);
            } 
            
            process(n);
        }
        
        return node;
    }
    
    /**
     * Add an assignment call to our list of calls.
     * 
     * @param assign the assignment node to add
     */
    private void addAssign(Node assign)
    {
        addAssign(assign, m_calls);
    }
    
    /**
     * Add an assignment call to the specified list of calls or increment the count if
     * that assignment is already there..
     * 
     * @param assign the assignment node to add
     * @param calls  the list of calls to add this assignment to
     */
    private void addAssign(Node assign, List<Call> calls)
    {
        if (assign.getChildCount() < 2) {
            /*
             This means it was a simple assignment to a constant value
             like var a = "foo" or var b = 5
             */
            return;
        }
        
        if (assign.getLastChild().getType() == Token.NAME) {
            /*
             This means it was assignment to a variable and since all
             variable names might be functions we need to add them to
             our calls list.
             */
            
            addCall(assign.getLastChild().getString(), assign, calls);
        } else if (assign.getFirstChild().getType() == Token.GETELEM &&
                   assign.getLastChild().getLastChild() != null &&
                   assign.getLastChild().getLastChild().getType() == Token.STRING) {
            /*
             This means it is an assignment to an array element like:
                 res[toString] = R._path2string;
             */
            addCall(assign.getLastChild().getLastChild().getString(), assign, calls);
        }
    }
    
    /**
     * Add a call to the specified list of calls or increment the call count if the call
     * is already in the list.
     * 
     * @param call     the call to add
     * @param callNode the Node representing this call
     * @param calls    the list to add it to
     */
    private void addCall(String call, Node callNode, List<Call> calls)
    {
        Call c = getCall(call, calls);

        if (c == null) {
            c = new Call(call);
            calls.add(c);
        } else {
            /*
             If the call is already there then we just increment
             the count
             */
            c.incCount();
        }

        if (m_forbiddenFunc.contains(c.getName())) {
            m_errors.add(callNode.getStaticSourceFile() + ": line " + callNode.getLineno() +
                               ", calling forbidden function " + c.getName());
        }
    }
    
    /**
     * Get the call object for the call with the specified name.
     * 
     * @param name   the call name to look for
     * @param calls  the list of calls to look in
     * 
     * @return the call if it was in the list of null if it wasn't
     */
    private static Call getCall(String name, List<Call> calls)
    {
        for (Call call : calls) {
            if (call.getName().equals(name)) {
                return call;
            }
        }
        
        return null;
    }
    
    /**
     * Add a call with the specified get property node.
     * 
     * @param getProp the node to add
     * @param calls   the list of calls to add it to
     */
    private void addCallsProp(Node getProp, List<Call> calls)
    {
        if (getProp.getLastChild().getType() == Token.STRING) {
            addCall(getProp.getLastChild().getString(), getProp, calls);
        }
        
        if (getProp.getFirstChild().getType() == Token.CALL) {
            /*
             Add the function name
             */
            addCall(getProp.getLastChild().getString(), getProp, calls);
            
            if (getProp.getFirstChild().getFirstChild().getType() == Token.NAME) {
                addCall(getProp.getFirstChild().getFirstChild().getString(), getProp, calls);
            }
        } else if (getProp.getFirstChild().getType() == Token.GETPROP) {
            addCallsProp(getProp.getFirstChild(), calls);
        }
        
        if (getProp.getNext() != null && getProp.getNext().getType() == Token.GETPROP) {
            addCallsProp(getProp.getNext(), calls);
        }
    }
    
    /**
     * Add all calls underneath the specified node.
     * 
     * @param call   the call to look in
     */
    private void addCalls(Node call)
    {
        addCalls(call, m_calls);
    }
    
    /**
     * Add all calls underneath the specified node.
     * 
     * @param call   the call to look in
     * @param calls  the list to add the call to
     */
    private void addCalls(Node call, List<Call> calls)
    {
        if (call.getType() == Token.GETPROP) {
            addCallsProp(call, calls);
        } else if (call.getFirstChild().getType() == Token.GETPROP) {
            addCallsProp(call.getFirstChild(), calls);
        } else if (call.getFirstChild().getType() == Token.NAME) {
            Node name = call.getFirstChild();
            addCall(name.getString(), name, calls);
            LOGGER.log(Level.FINE, "name.getString(): " + name.getString());
        } else if (call.getFirstChild().getType() == Token.GETELEM) {
            /*
             This is a call using the array index to get the function
             property like this:
     
             obj['hello']();
             */
            String c = getConcatenatedStringIndex(call.getFirstChild());
            if (c != null) {
                addCall(c, call, calls);
            }
        }
    }
    
    private String getConcatenatedStringIndex(Node getElem)
    {
        if (getElem.getFirstChild().getNext().getType() == Token.STRING) {
            /*
             Then this is a simple string reference like obj['hello']
             and we can just return the string
             */
            return getElem.getFirstChild().getNext().getString();
        } else if (getElem.getFirstChild().getNext().getType() == Token.ADD) {
            /*
             Then this is a concatenated string like obj['h' + 'el' + 'lo']
             */
            
            StringBuffer sb = new StringBuffer();
            Node current = getElem.getFirstChild().getNext();
            while (current != null) {
                if (current.getFirstChild().getType() == Token.ADD) {
                    String s = getString(current.getFirstChild().getNext());
                    if (s != null) {
                        sb.insert(0, s);
                    } else {
                        return null;
                    }
                    current = current.getFirstChild();
                } else if (current.getFirstChild().getType() == Token.STRING) {
                    String s = getString(current.getFirstChild().getNext());
                    if (s != null) {
                        sb.insert(0, s);
                    } else {
                        return null;
                    }
                    
                    s = getString(current.getFirstChild());
                    if (s != null) {
                        sb.insert(0, s);
                    } else {
                        return null;
                    }
                    current = null;
                } else {
                    current = null;
                }
            }
            
            return sb.toString();
        } else {
            /*
             Then this was some more complex type of string like
             obj[('h' + 'el' + 'lo').substring(2)].  We can't evaluate
             that string with just static evaluation so the user
             will have to declare an external for that.
             */
            return null;
        }
    }
    
    private String getString(Node n)
    {
        if (n.getType() == Token.STRING) {
            return n.getString();
        } else if (n.getType() == Token.NUMBER) {
            double num = n.getDouble();
            int inum = (int) num;
            if (inum == num) {
                return "" + inum;
            } else {
                return "" + num;
            }
        } else {
            return null;
        }
    }
    
    /**
     * Find all of the calls in the given function.
     * 
     * @param func   the function to look in
     * 
     * @return the list of calls
     */
    private Call[] findCalls(Node func)
    {
        ArrayList<Call> calls = new ArrayList<Call>();
        findCalls(func, calls);
        return calls.toArray(new Call[calls.size()]);
    }
    
    /**
     * Find all of the calls in the given function.
     * 
     * @param node   the node to look in
     * @param calls  the list of calls to add the function to
     */
    private void findCalls(Node node, List<Call> calls)
    {
        Iterator<Node> nodes = node.children().iterator();
        
        while (nodes.hasNext()) {
            Node n = nodes.next();
            if (n.getType() == Token.CALL || n.getType() == Token.NEW) {
                addCalls(n, calls);
            } else if (n.getType() == Token.ASSIGN ||
                       n.getType() == Token.ASSIGN_BITOR  ||
                       n.getType() == Token.ASSIGN_BITXOR ||
                       n.getType() == Token.ASSIGN_BITAND ||
                       n.getType() == Token.ASSIGN_LSH ||
                       n.getType() == Token.ASSIGN_RSH ||
                       n.getType() == Token.ASSIGN_URSH ||
                       n.getType() == Token.ASSIGN_ADD ||
                       n.getType() == Token.ASSIGN_SUB ||
                       n.getType() == Token.ASSIGN_MUL ||
                       n.getType() == Token.ASSIGN_DIV ||
                       n.getType() == Token.ASSIGN_MOD) {
                /*
                 This is an assignment operator.  
                 */
                addAssign(n, calls);
            } 
            
            findCalls(n, calls);
        }
    }
    
    /**
     * Get the name of the function at the specified node if this node represents an
     * interesting function.
     * 
     * @param n      the node to look under
     * 
     * @return the name of this function
     */
    private String getFunctionName(Node n)
    {
        try {
            if (n.getParent().getType() == Token.ASSIGN) {
                if (n.getParent().getFirstChild().getChildCount() == 0) {
                    /*
                     This is a variable assignment of a function to a
                     variable in the globabl scope.  These functions are
                     just too big in scope so we ignore them.  Example:
                        myVar = function()
                     */
                    return null;
                } else if (n.getParent().getFirstChild().getType() == Token.GETELEM) {
                    /*
                     This is a property assignment function with an array
                     index like this: 
                        jQuery.fn[ "inner" + name ] = function()
     
                     These functions are tricky to remove since we can't
                     depend on just the name when removing them.  We're
                     just leaving them for now.
                     */
                    String c = getConcatenatedStringIndex(n.getParent().getFirstChild());
                    if (c != null) {
                        return c;
                    } else {
                        return null;
                    }
                } else {
                    /*
                     This is a property assignment function like:
                        myObj.func1 = function()
                     */
                    return n.getParent().getFirstChild().getLastChild().getString();
                }
            }
            
            if (n.getParent().getType() == Token.STRING) {
                /*
                 This is a closure style function like this:
                     myFunc: function()
                 */
                return n.getParent().getString();
            } else {
                if (n.getFirstChild().getType() == Token.GETPROP) {
                    /*
                     This is a chain function assignment
                     */
                    return n.getFirstChild().getFirstChild().getNext().getString();
                } else {
                    /*
                     This is a standard type of function like this:
                        function myFunc()
                     */
                    return n.getFirstChild().getString();
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "npe: " + n.toStringTree());
            e.printStackTrace();
            throw new RuntimeException("stop here...");
        }
    }
    
    /**
     * Get the charset used by this compiler.
     * 
     * @return the charset
     */
    public String getCharset()
    {
        return m_charset;
    }
    
    /**
     * Set the charset used by this compiler.
     * 
     * @param charset the charset
     */
    public void setCharset(String charset)
    {
        m_charset = charset;
    }
    
    /**
     * Get the error manager for this compilation.  The error manager is never null, but it
     * can return a zero error count.
     * 
     * @return the error manager
     */
    public ErrorManager getErrorManager()
    {
        return m_errMgr;
    }
}


/**
 * This little console handler makes it possible to send Java logging to System.out 
 * instead of System.err.
 */
class SlimConsoleHandler extends ConsoleHandler
{
    protected void setOutputStream(OutputStream out) throws SecurityException
    {
        super.setOutputStream(System.out);
    }
}
