package com.andrewortman.reddcrawl;

import com.andrewortman.reddcrawl.services.BackendServicesConfiguration;
import com.andrewortman.reddcrawl.services.ServiceManager;
import com.andrewortman.reddcrawl.web.WebConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.resource.Resource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

import javax.annotation.Nonnull;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

public final class Application {
    @Nonnull
    public static ServletContextHandler getServletContextHandler(final WebApplicationContext context) {
        final ServletContextHandler contextHandler = new ServletContextHandler();
        contextHandler.addServlet(new ServletHolder(new DispatcherServlet(context)), "/api/*");

        final DefaultServlet defaultServlet = new DefaultServlet();
        final ServletHolder defaultServletHolder = new ServletHolder(defaultServlet);
        defaultServletHolder.setInitParameter("dirAllowed", "true");
        final URL resoureBase = Resource.newClassPathResource("/web ").getURL();
        defaultServletHolder.setInitParameter("resourceBase", resoureBase.toString());
        defaultServletHolder.setInitParameter("cacheControl", "max-age=3600,public");
        contextHandler.addServlet(defaultServletHolder, "/*");
        return contextHandler;
    }

    @Nonnull
    public static AnnotationConfigWebApplicationContext getContext() {
        final AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.setConfigLocation(WebConfiguration.class.getName());
        return context;
    }

    @Nonnull
    public static ServerConnector getServerConnector(final Server server, final int port) {
        final ServerConnector http = new ServerConnector(server,
                new HttpConnectionFactory());
        http.setPort(port);
        http.setIdleTimeout(30000);

        return http;
    }

    public static void main(final String[] args) throws Exception {
        final List<String> argList = Arrays.asList(args);

        if (argList.contains("--worker") && argList.contains("--web")) {
            System.out.println("Cannot start both worker and web app at the same time. Launch them both side by side instead");
            System.exit(1);
        }

        if (argList.contains("--web")) {
            final AnnotationConfigWebApplicationContext context = getContext();

            final Environment environment = context.getEnvironment();
            final int httpPort = environment.getProperty("http.port", Integer.class, 8085);

            final Server server = new Server();

            final ServletContextHandler servletContextHandler = getServletContextHandler(context);
            server.setHandler(servletContextHandler);
            server.addConnector(getServerConnector(server, httpPort));


            server.start();
            server.join();
        } else if (argList.contains("--worker")) {
            final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(BackendServicesConfiguration.class);

            final ServiceManager serviceManager = context.getBean(ServiceManager.class);
            serviceManager.startAllThreads();

            serviceManager.joinAllThreads();
        } else {
            System.out.println("Specify either --worker or --web");
            System.exit(1);
        }
    }
}
