package com.connecteamed.server.domain.collaboration.dto;

import lombok.Data;

@Data
public class SocketMessage {
    private String type;      // "JOIN", "UPDATE", "AWARENESS" 등
    private String docId;     // 문서 ID (방 번호)
    private String userId;    // 보낸 사용자 ID (서버에서 세팅)
    private String payload;   // Yjs Update Data (Base64 String)
    private Object awareness; 
}
