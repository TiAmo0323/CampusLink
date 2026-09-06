package com.campuslink;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.flywaydb.core.Flyway;
import com.campuslink.config.BootstrapData;
import com.campuslink.domain.CampusTask;
import com.campuslink.domain.TaskOrder;
import com.campuslink.mapper.TaskMapper;
import com.campuslink.mapper.TaskOrderMapper;
import com.campuslink.service.TaskTimeoutService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties={"spring.flyway.clean-disabled=false","debug=false","logging.level.root=WARN","campuslink.task-timeout-cron=-","campuslink.refresh-cleanup-cron=-"})
@AutoConfigureMockMvc
class CampusLinkWorkflowTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired TaskMapper taskMapper;
    @Autowired TaskOrderMapper orderMapper;
    @Autowired TaskTimeoutService timeoutService;
    @Autowired Flyway flyway;
    @Autowired BootstrapData bootstrapData;

    @BeforeEach
    void resetDatabase() throws Exception { flyway.clean(); flyway.migrate(); bootstrapData.run(); }

    @Test
    void completeTaskLifecycleSettlesPointsAndCredit() throws Exception {
        String alice=login("alice","demo123"), bob=login("bob","demo123");
        JsonNode list=getJson("/api/public/tasks?page=1&size=10",null);
        long taskId=list.at("/data/records/0/id").asLong();

        JsonNode apply=postJson("/api/tasks/"+taskId+"/applications",bob,"{\"message\":\"我有摄影经验，可以按时完成\"}");
        assertThat(apply.get("code").asInt()).isEqualTo(200);

        JsonNode applications=getJson("/api/tasks/"+taskId+"/applications",alice);
        long applicationId=applications.at("/data/records/0/application/id").asLong();
        JsonNode accepted=postJson("/api/applications/"+applicationId+"/accept",alice,"{}");
        long orderId=accepted.at("/data/order/id").asLong();
        assertThat(accepted.at("/data/order/status").asText()).isEqualTo("WAIT_EXECUTE");

        assertThat(postJson("/api/orders/"+orderId+"/start",bob,"{}").at("/data/order/status").asText()).isEqualTo("IN_PROGRESS");
        JsonNode completion=postJson("/api/orders/"+orderId+"/completions",bob,"{\"description\":\"拍摄完成，照片已整理上传\"}");
        assertThat(completion.at("/data/reviewStatus").asText()).isEqualTo("PENDING");
        assertThat(getJson("/api/orders/"+orderId,alice).at("/data/order/status").asText()).isEqualTo("WAIT_ACCEPTANCE");
        assertThat(postJson("/api/orders/"+orderId+"/approve",alice,"{}").at("/data/order/status").asText()).isEqualTo("COMPLETED");

        JsonNode bobWallet=getJson("/api/wallet",bob);
        JsonNode aliceWallet=getJson("/api/wallet",alice);
        assertThat(bobWallet.at("/data/availablePoints").asInt()).isEqualTo(340);
        assertThat(bobWallet.at("/data/creditScore").asInt()).isEqualTo(97);
        assertThat(aliceWallet.at("/data/frozenPoints").asInt()).isZero();

        long bobId=accepted.at("/data/accepter/id").asLong();
        JsonNode review=postJson("/api/reviews",alice,"{\"businessType\":\"TASK\",\"businessId\":"+orderId+",\"revieweeId\":"+bobId+",\"rating\":5,\"content\":\"认真负责\"}");
        assertThat(review.get("code").asInt()).isEqualTo(200);
        assertThat(getJson("/api/wallet",bob).at("/data/creditScore").asInt()).isEqualTo(99);
    }

    @Test
    void adminAndSkillMatchingEndpointsAreProtectedAndAvailable() throws Exception {
        String alice=login("alice","demo123"), admin=login("admin","admin123");
        JsonNode matches=getJson("/api/skills/matches",alice);
        assertThat(matches.get("code").asInt()).isEqualTo(200);
        assertThat(matches.at("/data/records").isArray()).isTrue();
        JsonNode dashboard=getJson("/api/admin/dashboard",admin);
        assertThat(dashboard.at("/data/users").asLong()).isGreaterThanOrEqualTo(2);
        JsonNode users=getJson("/api/admin/users?page=1&size=1",admin);
        assertThat(users.at("/data/records").size()).isEqualTo(1);
        assertThat(users.at("/data/total").asLong()).isGreaterThanOrEqualTo(3);
        mvc.perform(get("/api/admin/dashboard").header("Authorization","Bearer "+alice)).andExpect(status().isForbidden())
                .andExpect(result->assertThat(json.readTree(result.getResponse().getContentAsString()).get("code").asInt()).isEqualTo(403));
    }

    @Test
    void abnormalTaskCanBeRefundedByAdmin() throws Exception {
        StartedOrder started=prepareStartedOrder();String admin=login("admin","admin123");
        JsonNode abnormal=postJson("/api/tasks/"+started.taskId+"/abnormal",started.bobToken,"{\"reasonType\":\"DISPUTE\",\"description\":\"双方对交付要求存在争议\"}");
        long reportId=abnormal.at("/data/id").asLong();
        assertThat(abnormal.at("/data/status").asText()).isEqualTo("PROCESSING");
        JsonNode resolved=postJson("/api/admin/abnormal-tasks/"+reportId+"/resolve",admin,"{\"decision\":\"REFUND\",\"liableUserId\":3,\"creditDelta\":-5,\"result\":\"证据不足，积分退回发布者\"}");
        assertThat(resolved.at("/data/status").asText()).isEqualTo("RESOLVED");
        assertThat(getJson("/api/wallet",started.aliceToken).at("/data/availablePoints").asInt()).isEqualTo(500);
        assertThat(getJson("/api/wallet",started.bobToken).at("/data/creditScore").asInt()).isEqualTo(87);
        assertThat(getJson("/api/orders/"+started.orderId,started.bobToken).at("/data/order/status").asText()).isEqualTo("CANCELLED");
    }

    @Test
    void overdueTaskIsMarkedAbnormalOnlyOnce() throws Exception {
        StartedOrder started=prepareStartedOrder();CampusTask task=taskMapper.selectById(started.taskId);task.setTaskTime(LocalDateTime.now().minusDays(2));taskMapper.updateById(task);TaskOrder overdue=orderMapper.selectById(started.orderId);overdue.setDueAt(LocalDateTime.now().minusDays(1));orderMapper.updateById(overdue);
        assertThat(timeoutService.processOverdue(LocalDateTime.now())).isEqualTo(1);
        assertThat(timeoutService.processOverdue(LocalDateTime.now())).isZero();
        TaskOrder order=orderMapper.selectById(started.orderId);assertThat(order.getStatus()).isEqualTo("ABNORMAL");
        assertThat(getJson("/api/wallet",started.bobToken).at("/data/creditScore").asInt()).isEqualTo(90);
    }

    @Test
    void concurrentApplicationSelectionCreatesOneOrder() throws Exception {
        String alice=login("alice","demo123"),bob=login("bob","demo123");
        JsonNode charlieRegistration=postJson("/api/auth/register",null,"{\"username\":\"charlie\",\"password\":\"demo123\",\"nickname\":\"周然\",\"studentNo\":\"20260003\",\"email\":\"charlie@campus.edu.cn\"}");String charlie=charlieRegistration.at("/data/token").asText();
        long taskId=getJson("/api/public/tasks?page=1&size=10",null).at("/data/records/0/id").asLong();postJson("/api/tasks/"+taskId+"/applications",bob,"{\"message\":\"申请一\"}");postJson("/api/tasks/"+taskId+"/applications",charlie,"{\"message\":\"申请二\"}");JsonNode applications=getJson("/api/tasks/"+taskId+"/applications",alice);long first=applications.at("/data/records/0/application/id").asLong(),second=applications.at("/data/records/1/application/id").asLong();
        CompletableFuture<JsonNode> a=CompletableFuture.supplyAsync(()->postUnchecked("/api/applications/"+first+"/accept",alice));CompletableFuture<JsonNode> b=CompletableFuture.supplyAsync(()->postUnchecked("/api/applications/"+second+"/accept",alice));List<JsonNode> results=List.of(a.join(),b.join());assertThat(results.stream().filter(n->n.get("code").asInt()==200).count()).isEqualTo(1);assertThat(results.stream().filter(n->n.get("code").asInt()!=200).count()).isEqualTo(1);assertThat(orderMapper.selectCount(null)).isEqualTo(1);
    }

    @Test
    void imageUploadReturnsServableUrl() throws Exception {
        String token=login("alice","demo123");java.io.ByteArrayOutputStream image=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(1,1,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",image);MockMultipartFile file=new MockMultipartFile("file","proof.png","image/png",image.toByteArray());String response=mvc.perform(multipart("/api/files/images").file(file).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();JsonNode result=json.readTree(response);assertThat(result.get("code").asInt()).isEqualTo(200);assertThat(result.at("/data/url").asText()).startsWith("/uploads/");
    }

    private StartedOrder prepareStartedOrder() throws Exception {String alice=login("alice","demo123"),bob=login("bob","demo123");long taskId=getJson("/api/public/tasks?page=1&size=10",null).at("/data/records/0/id").asLong();postJson("/api/tasks/"+taskId+"/applications",bob,"{\"message\":\"可以完成\"}");long applicationId=getJson("/api/tasks/"+taskId+"/applications",alice).at("/data/records/0/application/id").asLong();long orderId=postJson("/api/applications/"+applicationId+"/accept",alice,"{}").at("/data/order/id").asLong();postJson("/api/orders/"+orderId+"/start",bob,"{}");return new StartedOrder(taskId,orderId,alice,bob);}
    private JsonNode postUnchecked(String path,String token){try{return postJson(path,token,"{}");}catch(Exception e){throw new RuntimeException(e);}}
    private record StartedOrder(long taskId,long orderId,String aliceToken,String bobToken){}

    private String login(String account,String password) throws Exception {return postJson("/api/auth/login",null,"{\"account\":\""+account+"\",\"password\":\""+password+"\"}").at("/data/token").asText();}
    private JsonNode getJson(String path,String token) throws Exception {var req=get(path);if(token!=null)req.header("Authorization","Bearer "+token);String body=mvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();return json.readTree(body);}
    private JsonNode postJson(String path,String token,String body) throws Exception {var req=post(path).contentType(MediaType.APPLICATION_JSON).content(body);if(token!=null)req.header("Authorization","Bearer "+token);String response=mvc.perform(req).andReturn().getResponse().getContentAsString();return json.readTree(response);}
}
