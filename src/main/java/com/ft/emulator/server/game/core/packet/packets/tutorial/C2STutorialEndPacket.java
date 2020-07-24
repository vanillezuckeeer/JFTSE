package com.ft.emulator.server.game.core.packet.packets.tutorial;

import com.ft.emulator.server.networking.packet.Packet;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class C2STutorialEndPacket extends Packet {

    private byte result;

    public C2STutorialEndPacket(Packet packet) {

        super(packet);

        this.result = this.readByte();
    }
}