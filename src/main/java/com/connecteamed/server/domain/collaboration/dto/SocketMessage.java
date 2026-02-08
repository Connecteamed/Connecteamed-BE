package com.connecteamed.server.domain.collaboration.dto;

import lombok.Data;

@Data
public class SocketMessage {

    // "JOIN", "UPDATE", "AWARENESS" 등
    private String type;

    // 문서 ID (방 번호)
    private String docId;

    // 보낸 사용자 ID (서버에서 세팅)
    private String userId;

    // Yjs Update Data (Base64 String)
    private String payload;

    // 압축된 Yjs 데이터 용
    private String content;

    private Object awareness; 
}
