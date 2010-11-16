/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.mobile.device.wurfl;

import java.io.IOException;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.sourceforge.wurfl.core.Device;
import net.sourceforge.wurfl.wng.Constants;
import net.sourceforge.wurfl.wng.WNGDevice;
import net.sourceforge.wurfl.wng.component.ComponentException;
import net.sourceforge.wurfl.wng.component.Document;
import net.sourceforge.wurfl.wng.component.StyleContainer;
import net.sourceforge.wurfl.wng.component.ValidatorVisitor;
import net.sourceforge.wurfl.wng.renderer.DefaultDocumentRenderer;
import net.sourceforge.wurfl.wng.renderer.DefaultRendererGroupResolver;
import net.sourceforge.wurfl.wng.renderer.DocumentRenderer;
import net.sourceforge.wurfl.wng.renderer.RenderedDocument;
import net.sourceforge.wurfl.wng.style.StyleOptimizerVisitor;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.functors.InstanceofPredicate;
import org.springframework.mobile.device.mvc.DeviceResolvingHandlerInterceptor;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * A Spring MVC Interceptor that renders a WNG {@link Document} after request completion, if one has been set in the current request.
 * As a mobile web view technology, WNG allows the developer to control the rendering of markup by device type in a declarative manner without resorting to manual if/else logic in his or her JSP templates.
 * When a WNG-based JSP view renders itself, the view builds a component tree that contains a {@link Document} object as its root element--no response writing is performed at that time.
 * After view rendering completes, this handler finishes WNG processing by rendering the assembled Document.  That action triggers the device markup to be generated and written to the response.
 * @author Keith Donald
 */
public class WngHandlerInterceptor implements HandlerInterceptor {

	private final DocumentRenderer documentRenderer;

	public WngHandlerInterceptor() {
		this.documentRenderer = new DefaultDocumentRenderer(new DefaultRendererGroupResolver());
	}

	public WngHandlerInterceptor(DocumentRenderer documentRenderer) {
		this.documentRenderer = documentRenderer;;
	}

	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
		return true;
	}

	public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
	}

	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
		if (isWngDocumentCreated(request)) {
			// logic adapted from WNGContextFilter which has the same responsibility
			WNGDevice device = new WNGDevice((Device) request.getAttribute(DeviceResolvingHandlerInterceptor.CURRENT_DEVICE_ATTRIBUTE));
			Document document = resolveDocument(request);
			StyleContainer styleContainer = (StyleContainer)CollectionUtils.find(document.getHead().getChildren(), new InstanceofPredicate(StyleContainer.class));
			if (styleContainer==null) {
				styleContainer = new StyleContainer();
				document.addToHead(styleContainer);
			}
			StyleOptimizerVisitor visitor = new StyleOptimizerVisitor(device, styleContainer);
			document.accept(visitor);
			RenderedDocument renderedDocument = documentRenderer.renderDocument(document, device);
			writeDocument(renderedDocument, response);			
		}
	}
	
	// internal helper;
	
	private boolean isWngDocumentCreated(ServletRequest request) {
		return request.getAttribute(Constants.ATT_DOCUMENT) != null;
	}
	
	private Document resolveDocument(ServletRequest request) throws ComponentException {
		Document document = (Document) request.getAttribute(Constants.ATT_DOCUMENT);
		ValidatorVisitor validatorVisitor = new ValidatorVisitor();
		document.accept(validatorVisitor);
		return document;
	}
	
	private void writeDocument(RenderedDocument renderedDocument, HttpServletResponse response) throws IOException {
		// reset the response in case a JSP happened to write some whitespace to it
		response.reset();
		response.setContentType(renderedDocument.getContentType());
		response.getWriter().print(renderedDocument.getMarkup());
		response.flushBuffer();
	}

}