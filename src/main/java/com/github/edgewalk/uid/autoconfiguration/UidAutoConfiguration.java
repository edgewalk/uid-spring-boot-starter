package com.github.edgewalk.uid.autoconfiguration;

import com.github.edgewalk.uid.Properties.UidProperties;
import com.github.edgewalk.uid.generator.UidGenerator;
import com.github.edgewalk.uid.generator.impl.DefaultUidGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Created by: edgewalk
 * 2018-11-26 17:21
 */
@Configuration
@EnableConfigurationProperties(UidProperties.class) //// 开启指定类的配置
@ConditionalOnProperty(name = "spring.uid.enable", havingValue = "true") // 指定的属性是否有指定的值
public class UidAutoConfiguration {

	@Autowired
	private UidProperties uidProperties;

	@Bean
	@ConditionalOnMissingBean  //当Spring Context中不存在该Bean时
	public UidGenerator uidGenerator() {
		return new DefaultUidGenerator(uidProperties);
	}
}
