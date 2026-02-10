package com.connecteamed.server.domain.task.controller;

import com.connecteamed.server.domain.task.dto.CompletedTaskDetailRes;
import com.connecteamed.server.domain.task.dto.CompletedTaskListRes;
import com.connecteamed.server.domain.task.dto.CompletedTaskStatusUpdateReq;
import com.connecteamed.server.domain.task.dto.CompletedTaskUpdateReq;
import com.connecteamed.server.domain.task.enums.TaskStatus;
import com.connecteamed.server.domain.task.service.CompletedTaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;


@ExtendWith(MockitoExtension.class)
class CompletedTaskControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private CompletedTaskService completedTaskService;

    @InjectMocks
    private CompletedTaskController completedTaskController;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(completedTaskController)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("GET /api/projects/{projectId}/tasks/completed 는 200과 목록을 반환한다")
    void getCompletedTasks_ok() throws Exception {
        when(completedTaskService.getCompletedTasks(1L)).thenReturn(new CompletedTaskListRes(List.of()));

        mockMvc.perform(get("/api/projects/{projectId}/tasks/completed", 1L))
                .andExpect(status().isOk());

        verify(completedTaskService, times(1)).getCompletedTasks(1L);
    }

    @Test
    @DisplayName("PATCH /api/tasks/{taskId}/status 는 상태 변경 후 200을 반환한다")
    void updateTaskStatus_ok() throws Exception {
        CompletedTaskStatusUpdateReq req = new CompletedTaskStatusUpdateReq(TaskStatus.DONE);
        String content = objectMapper.writeValueAsString(req);

        mockMvc.perform(patch("/api/tasks/{taskId}/status", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("업무 상태가 변경되었습니다."));

        verify(completedTaskService, times(1)).updateCompletedTaskStatus(eq(1L), any());
    }

    @Test
    @DisplayName("GET /api/tasks/{taskId} 는 상세 정보와 200을 반환한다")
    void getCompletedTaskDetail_ok() throws Exception {
        when(completedTaskService.getCompletedTaskDetail(1L)).thenReturn(null);

        mockMvc.perform(get("/api/tasks/{taskId}", 1L))
                .andExpect(status().isOk());

        verify(completedTaskService, times(1)).getCompletedTaskDetail(1L);
    }

    @Test
    @DisplayName("PATCH /api/tasks/{taskId} 는 수정 성공 시 200을 반환한다")
    void updateCompletedTask_ok() throws Exception {
        CompletedTaskUpdateReq req = new CompletedTaskUpdateReq("수정 제목", "DONE", List.of(1L, 2L), "2025.02.10", "2025.02.11", "수정 내용",  "회고 내용");
        String content = objectMapper.writeValueAsString(req);

        CompletedTaskDetailRes responseDto = new CompletedTaskDetailRes(
                1L, "수정 제목", "DONE", List.of(),
                null, null, "수정 내용", "회고 내용"
        );

        when(completedTaskService.updateCompletedTask(anyLong(), any(CompletedTaskUpdateReq.class)))
                .thenReturn(responseDto);

        mockMvc.perform(patch("/api/tasks/{taskId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value("요청에 성공했습니다."))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.title").value("수정 제목"));

        verify(completedTaskService, times(1)).updateCompletedTask(eq(1L), any(CompletedTaskUpdateReq.class));
    }

    @Test
    @DisplayName("DELETE /api/tasks/{taskId} 는 삭제 성공 시 200을 반환한다")
    void deleteCompletedTask_ok() throws Exception {
        mockMvc.perform(delete("/api/tasks/{taskId}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("업무가 삭제되었습니다."));

        verify(completedTaskService, times(1)).deleteCompletedTask(1L);
    }

    @Test
    @DisplayName("PATCH status: JSON이 깨져있으면 400을 반환한다")
    void updateTaskStatus_invalidJson_badRequest() throws Exception {
        mockMvc.perform(
                        patch("/api/tasks/{taskId}/status", 1L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"DON")
                )
                .andExpect(status().isBadRequest());

        verify(completedTaskService, never()).updateCompletedTaskStatus(anyLong(), any());
    }

    @Test
    @DisplayName("PATCH tasks: Body가 아예 비어있으면 400을 반환한다")
    void updateCompletedTask_invalidBody_badRequest() throws Exception {
        mockMvc.perform(
                        patch("/api/tasks/{taskId}", 1L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("")
                )
                .andExpect(status().isBadRequest());

        verify(completedTaskService, never()).updateCompletedTask(anyLong(), any());
    }
}