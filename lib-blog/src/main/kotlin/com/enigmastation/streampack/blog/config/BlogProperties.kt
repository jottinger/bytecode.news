/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** Configuration for the blog service module */
@ConfigurationProperties(prefix = "streampack.blog")
data class BlogProperties(val serviceId: String = "blog-service")
