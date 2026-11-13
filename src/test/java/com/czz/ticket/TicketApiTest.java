package com.czz.ticket;

import com.czz.ticket.config.SqlCounter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TicketApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private long createTicket(String title, String priority) throws Exception {
        String body = mvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"priority\":\"" + priority + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    @Test
    @DisplayName("创建工单：默认状态 OPEN，优先级缺省为 MEDIUM")
    void createTicketWithDefaults() throws Exception {
        mvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"登录页 500\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.priority").value("MEDIUM"))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    @DisplayName("创建工单：标题为空返回 400 并给出字段级原因")
    void createTicketRejectsBlankTitle() throws Exception {
        mvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0]", containsString("title")));
    }

    @Test
    @DisplayName("创建工单：未知优先级返回 400")
    void createTicketRejectsUnknownPriority() throws Exception {
        mvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"磁盘告警\",\"priority\":\"CRITICAL\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("查工单：不存在返回 404")
    void getMissingTicket() throws Exception {
        mvc.perform(get("/api/tickets/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("列表：按状态过滤，只返回匹配的工单")
    void listFiltersByStatus() throws Exception {
        long id = createTicket("导出超时", "HIGH");
        mvc.perform(post("/api/tickets/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/tickets").param("status", "IN_PROGRESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].status").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is("IN_PROGRESS"))));
    }

    @Test
    @DisplayName("列表：size 超过上限被截到 100，而不是把整表打出去")
    void listCapsPageSize() throws Exception {
        mvc.perform(get("/api/tickets").param("size", "100000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    @DisplayName("列表：20 条工单只下发 3 条 SQL（分页 / 批量处理人 / 总数），守住 N+1")
    void listDoesNotTriggerNPlusOne() throws Exception {
        for (int i = 0; i < 20; i++) {
            long id = createTicket("批量工单 " + i, "LOW");
            mvc.perform(post("/api/tickets/" + id + "/assign")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"assigneeId\":1}"))
                    .andExpect(status().isOk());
        }

        SqlCounter.reset();
        mvc.perform(get("/api/tickets").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(20));

        assertThat(SqlCounter.count())
                .as("列表接口的 SQL 条数不应随记录数增长")
                .isLessThanOrEqualTo(3);
    }

    @Test
    @DisplayName("派单：成功写入派单记录并把状态推到 ASSIGNED")
    void assignTicket() throws Exception {
        long id = createTicket("支付回调丢失", "URGENT");
        mvc.perform(post("/api/tickets/" + id + "/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeId\":2,\"note\":\"今天内排查\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.assigneeId").value(2))
                .andExpect(jsonPath("$.assigneeName").value("赵工"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    @DisplayName("派单：同一工单第二次派单返回 409，不会悄悄覆盖处理人")
    void assignTwiceConflicts() throws Exception {
        long id = createTicket("报表数字不对", "HIGH");
        mvc.perform(post("/api/tickets/" + id + "/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeId\":1}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/tickets/" + id + "/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeId\":3}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    @DisplayName("派单：给已停用的处理人派单返回 409")
    void assignToInactiveAssignee() throws Exception {
        long id = createTicket("缓存击穿", "MEDIUM");
        mvc.perform(post("/api/tickets/" + id + "/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeId\":4}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("派单：处理人不存在返回 404")
    void assignToMissingAssignee() throws Exception {
        long id = createTicket("慢查询", "LOW");
        mvc.perform(post("/api/tickets/" + id + "/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeId\":999}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("状态机：OPEN -> CLOSED 允许，CLOSED 之后任何流转都拒绝")
    void closedTicketIsTerminal() throws Exception {
        long id = createTicket("重复报障", "LOW");
        mvc.perform(post("/api/tickets/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        mvc.perform(post("/api/tickets/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("状态机：OPEN 不能直接跳到 RESOLVED")
    void rejectsIllegalTransition() throws Exception {
        long id = createTicket("越级流转", "MEDIUM");
        mvc.perform(post("/api/tickets/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("状态机：RESOLVED 会写入 resolved_at，重新打开时清空")
    void resolvedAtTracksReopen() throws Exception {
        long id = createTicket("已修复但复发", "HIGH");
        mvc.perform(post("/api/tickets/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/tickets/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolvedAt").isNotEmpty());

        mvc.perform(post("/api/tickets/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolvedAt").doesNotExist());
    }

    @Test
    @DisplayName("状态机：非法状态值返回 400")
    void rejectsUnknownStatus() throws Exception {
        long id = createTicket("瞎写状态", "LOW");
        mvc.perform(post("/api/tickets/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DONE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("统计：总数与按状态分组对得上")
    void statsAggregates() throws Exception {
        long a = createTicket("统计用 A", "HIGH");
        long b = createTicket("统计用 B", "HIGH");
        mvc.perform(post("/api/tickets/" + a + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk());
        createTicket("统计用 C", "LOW");

        mvc.perform(get("/api/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.byStatus[?(@.key=='OPEN')].count").exists())
                .andExpect(jsonPath("$.byPriority[?(@.key=='HIGH')].count").exists());
    }

    @Test
    @DisplayName("处理人：只读接口返回种子数据且包含启用标记")
    void listAssignees() throws Exception {
        mvc.perform(get("/api/assignees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[3].active").value(false));
    }
}
