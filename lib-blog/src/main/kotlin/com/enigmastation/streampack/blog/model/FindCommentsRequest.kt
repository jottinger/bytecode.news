/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.model

import java.util.UUID

/** Request to retrieve all comments for a post as a threaded tree */
data class FindCommentsRequest(val postId: UUID)
