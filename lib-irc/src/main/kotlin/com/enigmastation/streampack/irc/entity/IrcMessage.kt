/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.irc.entity

import com.enigmastation.streampack.irc.model.IrcMessageType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID
import org.hibernate.annotations.UuidGenerator

/** Immutable IRC message log entry */
@Entity
@Table(name = "irc_messages")
data class IrcMessage(
    @Id @UuidGenerator(style = UuidGenerator.Style.VERSION_7) val id: UUID = UUID(0, 0),
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id", nullable = false)
    val channel: IrcChannel = IrcChannel(),
    @Column(nullable = false, length = 50) val nick: String = "",
    @Column(nullable = false, columnDefinition = "TEXT") val content: String = "",
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    val messageType: IrcMessageType = IrcMessageType.MESSAGE,
    @Column(nullable = false) val timestamp: Instant = Instant.now(),
)
