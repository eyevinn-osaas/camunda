package org.camunda.optimize.test;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response.Status;

import org.camunda.bpm.engine.rest.security.auth.ProcessEngineAuthenticationFilter;

public class CustomAuthFilter extends ProcessEngineAuthenticationFilter {

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
    HttpServletRequest req = (HttpServletRequest) request;
    HttpServletResponse resp = (HttpServletResponse) response;
    
    String customToken = req.getHeader("Custom-Token");
    
    if (! "SomeCustomToken".equals(customToken)) {
      resp.setStatus(Status.UNAUTHORIZED.getStatusCode());
    } else {
      super.doFilter(request, response, chain);
    }
  }

}
