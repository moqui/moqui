/*
 * Copyright 2010 David E. Jones
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.moqui.context;

import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import freemarker.ext.servlet.HttpRequestHashModel;
import freemarker.ext.servlet.HttpSessionHashModel;
import freemarker.ext.servlet.ServletContextHashModel;

/**
 * Interface definition for object used throughout the Moqui Framework to manage contextual execution information and tool interfaces.
 */

public interface WebExecutionContext extends ExecutionContext {
    Map<String, Object> getParameters();

    HttpServletRequest getRequest();
    HttpRequestHashModel getRequestAttributes();
    Map<String, ?> getRequestParameters();

    HttpServletResponse getResponse();

    HttpSession getSession();
    HttpSessionHashModel getSessionAttributes();

    ServletContext getServletContext();
}
