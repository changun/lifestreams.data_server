package org.ohmage.lifestreams.utils.data_server;

import org.apache.catalina.connector.Connector;
import org.apache.coyote.http11.AbstractHttp11Protocol;
import org.ohmage.models.OhmageServer;
import org.ohmage.models.OhmageUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.embedded.ConfigurableEmbeddedServletContainer;
import org.springframework.boot.context.embedded.EmbeddedServletContainerCustomizer;
import org.springframework.boot.context.embedded.tomcat.TomcatConnectorCustomizer;
import org.springframework.boot.context.embedded.tomcat.TomcatEmbeddedServletContainerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;
import org.springframework.http.MediaType;

@Configuration
@ComponentScan(basePackages={"org.ohmage.lifestreams.utils"})
@EnableAutoConfiguration
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Value("${ohmage.server}")
    String ohmageServerAddress;
    @Bean
    public OhmageServer ohmageServer(){
    	return new OhmageServer(ohmageServerAddress);
    }
    
    // enable compression in Tomcat
	@Bean
	public EmbeddedServletContainerCustomizer servletContainerCustomizer() {
		return new EmbeddedServletContainerCustomizer() {
			@Override
			public void customize(ConfigurableEmbeddedServletContainer factory) {
				((TomcatEmbeddedServletContainerFactory) factory)
						.addConnectorCustomizers(new TomcatConnectorCustomizer() {
							@Override
							public void customize(Connector connector) {
								AbstractHttp11Protocol httpProtocol = (AbstractHttp11Protocol) connector
										.getProtocolHandler();
								httpProtocol.setCompression("on");
								httpProtocol.setCompressionMinSize(1);
								httpProtocol.setCompressableMimeType(MediaType.APPLICATION_JSON_VALUE);
							}
						});
			}
		};
	}
}
