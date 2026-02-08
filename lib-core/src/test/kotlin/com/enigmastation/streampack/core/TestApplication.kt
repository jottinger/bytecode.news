/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.core

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan

/** Test-only application class required by @DataJpaTest as a scan root */
@SpringBootApplication
@ConfigurationPropertiesScan("com.enigmastation.streampack")
class TestApplication
