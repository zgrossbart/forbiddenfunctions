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

/**
 * This class represents a single source file for the compiler, either main source or 
 * library source.
 */
public class JSFile
{
    private String m_name;
    private String m_content;
    
    /**
     * Create a new JSFile.
     * 
     * @param name    the name of the file
     * @param content the content of the file
     */
    public JSFile(String name, String content)
    {
        m_name = name;
        m_content = content;
    }
    
    /**
     * Get the name of the file.
     * 
     * @return the file name
     */
    public String getName()
    {
        return m_name;
    }
    
    /**
     * Get the content of the file.
     * 
     * @return the file content
     */
    public String getContent()
    {
        return m_content;
    }
    
    @Override
    public String toString()
    {
        return "JSFile: " + m_name;
    }
}
