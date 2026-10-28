package com.xiaoying.studio.asset;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/** 未携带有效签名的 /files/** 请求一律 403。 */
public class FileAccessFilter extends OncePerRequestFilter {

    private final FileUrlSigner signer;

    public FileAccessFilter(FileUrlSigner signer) {
        this.signer = signer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (signer.verify(request.getRequestURI(), request.getParameter("exp"), request.getParameter("sig"))) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"code\":\"FORBIDDEN\",\"message\":\"文件链接无效或已过期\"}");
    }

    @Configuration
    static class Registration {
        @Bean
        FilterRegistrationBean<FileAccessFilter> fileAccessFilter(FileUrlSigner signer) {
            FilterRegistrationBean<FileAccessFilter> bean = new FilterRegistrationBean<>(new FileAccessFilter(signer));
            bean.addUrlPatterns("/files/*");
            return bean;
        }
    }
}
