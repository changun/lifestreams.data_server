package org.ohmage.lifestreams.utils.data_server;

import org.ohmage.lifestreams.AppConfig;
import org.ohmage.models.OhmageServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.Environment;

@Configuration
@EnableAutoConfiguration
@PropertySource("/stream.server.properties")
public class Application extends AppConfig {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Autowired
    Environment env;
    @Bean
    public OhmageServer ohmageServer(){
            return new OhmageServer(env.getProperty("ohmage.server"));
    }
    @Bean
    public MainCotroller mainController(){
        return new MainCotroller(ohmageServer(), streamStore(),
                Integer.parseInt(env.getProperty("token.life.time")),
                Integer.parseInt(env.getProperty("redis.token.store.DBIndex")));
    }
    @Bean
    public SimpleCORSFilter filter(){
        return new SimpleCORSFilter();
    }
}
    /*
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
	}*/

