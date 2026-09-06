package com.campuslink;
import com.fasterxml.jackson.databind.*;
import com.campuslink.mapper.*;import com.campuslink.domain.*;import com.campuslink.config.BootstrapData;import com.campuslink.service.PointCreditService;
import org.flywaydb.core.Flyway;import org.junit.jupiter.api.*;import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;import org.springframework.test.web.servlet.MockMvc;import org.springframework.jdbc.core.JdbcTemplate;
import java.time.LocalDateTime;import java.util.*;import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
@SpringBootTest(properties={"spring.flyway.clean-disabled=false","debug=false","logging.level.root=WARN","campuslink.upload-dir=target/test-uploads","campuslink.task-timeout-cron=-","campuslink.refresh-cleanup-cron=-"})
@AutoConfigureMockMvc class RevisionWorkflowTest {
 @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired Flyway flyway;@Autowired BootstrapData bootstrap;@Autowired JdbcTemplate jdbc;
 @Autowired UserMapper users;@Autowired PointCreditService credit;
 @BeforeEach void reset()throws Exception{flyway.clean();flyway.migrate();bootstrap.run();}
 JsonNode req(String method,String url,String token,Object body)throws Exception{
  var r=switch(method){case "GET"->get(url);case "PUT"->put(url);default->post(url);};
  if(token!=null)r.header("Authorization","Bearer "+token);if(body!=null)r.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
  return json.readTree(mvc.perform(r).andReturn().getResponse().getContentAsString());
 }
 JsonNode loginData(String account)throws Exception{return req("POST","/api/auth/login",null,Map.of("account",account,"password",account.equals("admin")?"admin123":"demo123")).path("data");}
 String login(String account)throws Exception{return loginData(account).path("token").asText();}
 long task()throws Exception{return req("GET","/api/public/tasks",null,null).at("/data/records/0/id").asLong();}
 long apply(long task,String token)throws Exception{assertThat(req("POST","/api/tasks/"+task+"/applications",token,Map.of("message","测试申请")).path("code").asInt()).isEqualTo(200);return req("GET","/api/tasks/mine?kind=applied",token,null).at("/data/records/0/application/id").asLong();}
 long accept(long aid,String token)throws Exception{JsonNode n=req("POST","/api/applications/"+aid+"/accept",token,Map.of());assertThat(n.path("code").asInt()).isEqualTo(200);return n.at("/data/order/id").asLong();}

 @Test void expiredSignedJwtAndRefreshAreRejected()throws Exception{
  String token=login("alice");String[] parts=token.split("\\.");
  var claims=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(Base64.getUrlDecoder().decode(parts[1]));claims.put("exp",java.time.Instant.now().minusSeconds(1).getEpochSecond());
  String data=parts[0]+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(json.writeValueAsBytes(claims));
  javax.crypto.Mac mac=javax.crypto.Mac.getInstance("HmacSHA256");mac.init(new javax.crypto.spec.SecretKeySpec("campuslink-course-design-change-in-production".getBytes(java.nio.charset.StandardCharsets.UTF_8),"HmacSHA256"));
  assertThat(req("GET","/api/users/me",data+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(data.getBytes(java.nio.charset.StandardCharsets.UTF_8))),null).path("code").asInt()).isEqualTo(401);
  JsonNode s=loginData("alice");jdbc.update("UPDATE refresh_session SET expires_at=?",LocalDateTime.now().minusMinutes(1));
  assertThat(req("POST","/api/auth/refresh",null,Map.of("refreshToken",s.path("refreshToken").asText())).path("code").asInt()).isEqualTo(401);
 }
 @Test void exchangeRequiresProviderThenRequesterConfirmation()throws Exception{
  String a=login("alice"),b=login("bob"),c=login("charlie");long bob=jdbc.queryForObject("SELECT id FROM `user` WHERE username='bob'",Long.class);
  JsonNode made=req("POST","/api/exchanges",a,Map.of("providerId",bob,"requestSkillId",3));assertThat(made.path("code").asInt()).isEqualTo(200);long id=made.at("/data/id").asLong();String url="/api/exchanges/"+id+"/";
  assertThat(req("POST",url+"accept",c,Map.of()).path("code").asInt()).isNotEqualTo(200);
  assertThat(req("POST",url+"accept",b,Map.of()).at("/data/status").asText()).isEqualTo("ACCEPTED");
  assertThat(req("POST",url+"start",a,Map.of()).at("/data/status").asText()).isEqualTo("IN_PROGRESS");
  assertThat(req("POST",url+"complete",a,Map.of()).path("code").asInt()).isEqualTo(400);
  JsonNode marked=req("POST",url+"complete",b,Map.of());assertThat(marked.at("/data/status").asText()).isEqualTo("IN_PROGRESS");assertThat(marked.at("/data/providerCompletedAt").asText()).isNotBlank();
  assertThat(req("POST",url+"complete",a,Map.of()).at("/data/status").asText()).isEqualTo("COMPLETED");
  assertThat(req("POST",url+"cancel",b,Map.of()).path("code").asInt()).isEqualTo(400);
 }
 @Test void uploadsRejectSpoofedImagesAndEnforceSeparateLimits()throws Exception{
  String t=login("alice");
  var fake=new org.springframework.mock.web.MockMultipartFile("file","fake.png","image/png","not an image".getBytes());
  assertThat(mvc.perform(multipart("/api/files/images").file(fake).header("Authorization","Bearer "+t)).andReturn().getResponse().getStatus()).isEqualTo(400);
  var large=new org.springframework.mock.web.MockMultipartFile("file","large.png","image/png",new byte[5*1024*1024+1]);
  assertThat(mvc.perform(multipart("/api/files/images").file(large).header("Authorization","Bearer "+t)).andReturn().getResponse().getStatus()).isEqualTo(400);
  var pdf=new org.springframework.mock.web.MockMultipartFile("file","proof.pdf","application/pdf","%PDF-1.4\n1 0 obj<</Type/Catalog>>endobj\n%%EOF".getBytes());
  assertThat(mvc.perform(multipart("/api/files/images").file(pdf).header("Authorization","Bearer "+t)).andReturn().getResponse().getStatus()).isEqualTo(400);
  assertThat(mvc.perform(multipart("/api/files/proofs").file(pdf).header("Authorization","Bearer "+t)).andReturn().getResponse().getStatus()).isEqualTo(200);
 }
 @Test void completionProofMustBeAStoredProofUpload()throws Exception{
  String alice=login("alice"),bob=login("bob");long id=task(),order=accept(apply(id,bob),alice);req("POST","/api/orders/"+order+"/start",bob,Map.of());
  assertThat(req("POST","/api/orders/"+order+"/completions",bob,Map.of("description","外链凭证","proofUrl","https://example.test/proof.pdf")).path("code").asInt()).isEqualTo(400);
  assertThat(req("POST","/api/orders/"+order+"/completions",bob,Map.of("description","不存在凭证","proofUrl","/uploads/20260906/00000000-0000-0000-0000-000000000000.pdf")).path("code").asInt()).isEqualTo(400);
  var pdf=new org.springframework.mock.web.MockMultipartFile("file","proof.pdf","application/pdf","%PDF-1.4\n1 0 obj<</Type/Catalog>>endobj\n%%EOF".getBytes());
  String uploaded=mvc.perform(multipart("/api/files/proofs").file(pdf).header("Authorization","Bearer "+bob)).andReturn().getResponse().getContentAsString();
  String proofUrl=json.readTree(uploaded).at("/data/url").asText();
  JsonNode completion=req("POST","/api/orders/"+order+"/completions",bob,Map.of("description","站内凭证","proofUrl",proofUrl));
  assertThat(completion.path("code").asInt()).isEqualTo(200);assertThat(completion.at("/data/proofUrl").asText()).isEqualTo(proofUrl);
 }
 @Test void paginationPreservesTotalsAndCapsRequestedSize()throws Exception{
  for(int i=0;i<60;i++)jdbc.update("INSERT INTO skill(name,category,status,created_at) VALUES(?,?,?,?)","分页测试"+i,"测试","ENABLED",LocalDateTime.now());
  JsonNode first=req("GET","/api/public/skills?size=999",null,null).path("data");
  assertThat(first.path("size").asInt()).isEqualTo(50);assertThat(first.path("records").size()).isEqualTo(50);assertThat(first.path("total").asLong()).isGreaterThan(50);
  JsonNode next=req("GET","/api/public/skills?page=2&size=50",null,null).path("data");
  assertThat(next.path("total")).isEqualTo(first.path("total"));assertThat(next.path("records").size()).isEqualTo((int)first.path("total").asLong()-50);
 }

 @Test void jwtRefreshRotationLogoutAndTampering()throws Exception{
  JsonNode s=loginData("alice");String token=s.path("token").asText(),refresh=s.path("refreshToken").asText();
  assertThat(token.split("\\.")).hasSize(3);JsonNode claims=json.readTree(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
  assertThat(claims.path("exp").asLong()-claims.path("iat").asLong()).isEqualTo(7200);
  assertThat(req("GET","/api/users/me",token+"x",null).path("code").asInt()).isEqualTo(401);
  JsonNode rotated=req("POST","/api/auth/refresh",null,Map.of("refreshToken",refresh));assertThat(rotated.path("code").asInt()).isEqualTo(200);
  assertThat(req("POST","/api/auth/refresh",null,Map.of("refreshToken",refresh)).path("code").asInt()).isEqualTo(401);
  assertThat(req("POST","/api/auth/logout",null,Map.of("refreshToken",rotated.at("/data/refreshToken").asText())).path("code").asInt()).isEqualTo(200);
  assertThat(req("GET","/api/users/me",rotated.at("/data/token").asText(),null).path("code").asInt()).isEqualTo(401);
 }
 @Test void failedLoginLocksAndBanRevokesOldTokens()throws Exception{
  for(int i=0;i<5;i++)assertThat(req("POST","/api/auth/login",null,Map.of("account","bob","password","incorrect")).path("code").asInt()).isEqualTo(401);
  assertThat(req("POST","/api/auth/login",null,Map.of("account","bob","password","demo123")).path("code").asInt()).isEqualTo(429);
  jdbc.update("UPDATE `user` SET locked_until=? WHERE username='bob'",LocalDateTime.now().minusMinutes(1));
  JsonNode s=loginData("bob");String token=s.path("token").asText(),admin=login("admin");long uid=s.at("/user/id").asLong();
  assertThat(jdbc.queryForObject("SELECT failed_login_attempts FROM `user` WHERE id=?",Integer.class,uid)).isZero();
  assertThat(req("PUT","/api/admin/users/"+uid+"/status/BANNED",admin,null).path("code").asInt()).isEqualTo(200);
  assertThat(req("GET","/api/users/me",token,null).path("code").asInt()).isEqualTo(401);
  req("PUT","/api/admin/users/"+uid+"/status/NORMAL",admin,null);
  assertThat(req("GET","/api/users/me",token,null).path("code").asInt()).isEqualTo(401);assertThat(login("bob")).isNotBlank();
 }
 @Test void withdrawalPreservesHistoryAndOwnership()throws Exception{
  String alice=login("alice"),bob=login("bob");long id=task(),a=apply(id,bob);
  assertThat(req("POST","/api/applications/"+a+"/withdraw",alice,Map.of()).path("code").asInt()).isEqualTo(403);
  assertThat(req("POST","/api/applications/"+a+"/withdraw",bob,Map.of()).path("code").asInt()).isEqualTo(200);
  assertThat(req("GET","/api/tasks/mine?kind=applied",bob,null).at("/data/records/0/application/status").asText()).isEqualTo("WITHDRAWN");
  assertThat(req("POST","/api/applications/"+a+"/accept",alice,Map.of()).path("code").asInt()).isEqualTo(400);
 }
 @Test void waitingCancellationRefundsExactlyOnce()throws Exception{
  String alice=login("alice"),bob=login("bob");long id=task();accept(apply(id,bob),alice);
  assertThat(req("POST","/api/tasks/"+id+"/cancel",bob,Map.of()).path("code").asInt()).isEqualTo(400);
  assertThat(req("POST","/api/tasks/"+id+"/cancel",bob,Map.of("reason","无法按期履约")).path("code").asInt()).isEqualTo(200);
  assertThat(req("POST","/api/tasks/"+id+"/cancel",bob,Map.of("reason","重复请求")).path("code").asInt()).isEqualTo(400);
  assertThat(req("GET","/api/wallet",alice,null).at("/data/availablePoints").asInt()).isEqualTo(500);
  assertThat(req("GET","/api/wallet",bob,null).at("/data/creditScore").asInt()).isEqualTo(87);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM point_transaction WHERE business_type='TASK_REFUND'",Integer.class)).isEqualTo(1);
  assertThat(jdbc.queryForObject("SELECT after_frozen FROM point_transaction WHERE business_type='TASK_REFUND'",Integer.class)).isZero();
 }
 @Test void rejectionLimitReassignsWithoutLosingEvidence()throws Exception{
  String alice=login("alice"),bob=login("bob");String charlie=req("POST","/api/auth/register",null,Map.of("username","charlie","password","demo123","nickname","新接取者","studentNo","2026090501")).at("/data/token").asText();
  long id=task(),ba=apply(id,bob),ca=apply(id,charlie),order=accept(ba,alice);req("POST","/api/orders/"+order+"/start",bob,Map.of());
  for(int i=0;i<3;i++){req("POST","/api/orders/"+order+"/completions",bob,Map.of("description","第"+i+"次成果"));req("POST","/api/orders/"+order+"/reject",alice,Map.of("reason","需要补充内容"));}
  assertThat(req("GET","/api/orders/"+order,alice,null).at("/data/order/status").asText()).isEqualTo("ABNORMAL");
  JsonNode next=req("POST","/api/tasks/"+id+"/reassign",alice,Map.of("applicationId",ca,"dueAt",LocalDateTime.now().plusDays(3).toString(),"reason","多次验收不通过"));
  assertThat(next.at("/data/order/id").asLong()).isEqualTo(order);assertThat(next.at("/data/order/assignmentRound").asInt()).isEqualTo(2);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM task_completion WHERE order_id=?",Integer.class,order)).isEqualTo(3);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_assignment_history WHERE order_id=?",Integer.class,order)).isEqualTo(1);
  assertThat(req("POST","/api/orders/"+order+"/start",bob,Map.of()).path("code").asInt()).isEqualTo(403);
  assertThat(req("GET","/api/orders/"+order,bob,null).at("/data/historical").asBoolean()).isTrue();
  assertThat(req("POST","/api/orders/"+order+"/start",charlie,Map.of()).path("code").asInt()).isEqualTo(200);
 }
 @Test void creditBoundaryKeepsZeroChangeAudit()throws Exception{
  User bob=users.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>().eq(User::getUsername,"bob"));
  jdbc.update("UPDATE `user` SET credit_score=100 WHERE id=?",bob.getId());credit.changeCredit(bob,5,"TASK_COMPLETION",null,"上限事件");
  assertThat(jdbc.queryForObject("SELECT change_value FROM credit_record WHERE reason='上限事件'",Integer.class)).isZero();
 }
 @Test void reportClaimAndDelist()throws Exception{
  String bob=login("bob"),admin=login("admin");long id=task();
  assertThat(req("POST","/api/reports",bob,Map.of("targetType","TASK","targetId",999999,"reasonType","OTHER")).path("code").asInt()).isEqualTo(400);
  long report=req("POST","/api/reports",bob,Map.of("targetType","TASK","targetId",id,"reasonType","VIOLATION","description","违规测试")).at("/data/id").asLong();
  assertThat(req("POST","/api/admin/reports/"+report+"/claim",admin,Map.of()).at("/data/status").asText()).isEqualTo("PROCESSING");
  assertThat(req("POST","/api/admin/tasks/"+id+"/delist",bob,Map.of("reason","无权下架")).path("code").asInt()).isEqualTo(403);
  assertThat(req("POST","/api/admin/tasks/"+id+"/delist",admin,Map.of("reason","核实违规")).path("code").asInt()).isEqualTo(200);
  assertThat(req("GET","/api/public/tasks/"+id,null,null).path("code").asInt()).isEqualTo(404);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM task WHERE id=?",Integer.class,id)).isEqualTo(1);
 }
 @Test void concurrentRefreshRotatesOnlyOnce()throws Exception{
  String refresh=loginData("alice").path("refreshToken").asText();try(var pool=Executors.newFixedThreadPool(2)){
   Callable<Integer> work=()->req("POST","/api/auth/refresh",null,Map.of("refreshToken",refresh)).path("code").asInt();
   var results=pool.invokeAll(List.of(work,work));assertThat(List.of(results.get(0).get(),results.get(1).get())).containsExactlyInAnyOrder(200,401);
  }
 }
 @Test void compatibilityValidationAndInitializationContracts()throws Exception{
  String alice=login("alice"),bob=login("bob");long id=task();
  JsonNode profile=req("GET","/api/skills/profile?offerPage=1&offerSize=1&needPage=1&needSize=1",alice,null).path("data");assertThat(profile.has("offersTotal")).isTrue();assertThat(profile.has("needsTotal")).isTrue();
  assertThat(req("GET","/api/skills/match",alice,null).path("code").asInt()).isEqualTo(200);assertThat(req("GET","/api/points/transactions",alice,null).path("code").asInt()).isEqualTo(200);
  JsonNode application=req("POST","/api/tasks/"+id+"/applications",bob,Map.of("message","兼容路径申请"));assertThat(application.at("/data/id").asLong()).isPositive();
  assertThat(req("POST","/api/tasks/"+id+"/applications/"+application.at("/data/id").asLong()+"/accept",alice,Map.of()).at("/data/order/id").asLong()).isPositive();
  JsonNode applicant=req("GET","/api/tasks/"+id+"/applicants/"+application.at("/data/applicantId").asLong()+"?skillPage=1&skillSize=1&reviewPage=1&reviewSize=1",alice,null).path("data");assertThat(applicant.has("skillsTotal")).isTrue();assertThat(applicant.has("reviewsTotal")).isTrue();
  JsonNode alias=req("POST","/api/users/skills",alice,Map.of("skillId",4,"proficiency","INTERMEDIATE","description","辅导高等数学"));assertThat(alias.at("/data/id").asLong()).isPositive();
  assertThat(req("GET","/api/public/tasks?page=abc",null,null).path("code").asInt()).isEqualTo(400);JsonNode missing=req("GET","/api/not-a-real-route",alice,null);assertThat(missing.path("code").asInt()).isEqualTo(404);assertThat(missing.has("data")).isTrue();assertThat(missing.path("data").isNull()).isTrue();
  String username="ledger"+System.nanoTime();JsonNode registered=req("POST","/api/auth/register",null,Map.of("username",username,"password","demo123","nickname","账本测试","studentNo","2026090599"));long uid=registered.at("/data/user/id").asLong();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM credit_record WHERE user_id=? AND business_type='REGISTER_INIT'",Integer.class,uid)).isEqualTo(1);
  assertThat(req("POST","/api/auth/register",null,Map.of("username","invalid"+System.nanoTime(),"password","demo123","nickname","校验测试","studentNo","!!!")).path("code").asInt()).isEqualTo(400);
 }
 @Test void disabledSkillCannotBeAcceptedAndGovernanceIsAudited()throws Exception{
  String alice=login("alice"),bob=login("bob"),admin=login("admin");long bobId=jdbc.queryForObject("SELECT id FROM `user` WHERE username='bob'",Long.class);
  long exchange=req("POST","/api/exchanges",alice,Map.of("providerId",bobId,"requestSkillId",3)).at("/data/id").asLong();assertThat(req("PUT","/api/admin/skills/3/status/DISABLED",admin,Map.of("reason","测试停用" )).path("code").asInt()).isEqualTo(200);assertThat(req("POST","/api/exchanges/"+exchange+"/accept",bob,Map.of()).path("code").asInt()).isEqualTo(400);
  int before=jdbc.queryForObject("SELECT credit_score FROM `user` WHERE id=?",Integer.class,bobId);long report=req("POST","/api/reports",alice,Map.of("targetType","USER","targetId",bobId,"reasonType","VIOLATION","description","治理动作测试")).at("/data/id").asLong();
  assertThat(req("POST","/api/admin/reports/"+report+"/handle",admin,Map.of("decision","CONFIRM_VIOLATION","liableUserId",bobId,"result","确认存在违规行为")).at("/data/status").asText()).isEqualTo("RESOLVED");assertThat(jdbc.queryForObject("SELECT credit_score FROM `user` WHERE id=?",Integer.class,bobId)).isEqualTo(before-10);assertThat(jdbc.queryForObject("SELECT handle_result FROM report WHERE id=?",String.class,report)).contains("CONFIRM_VIOLATION[USER#"+bobId+"]");assertThat(req("GET","/api/admin/reports/"+report+"/evidence",admin,null).at("/data/report/handleResult").asText()).contains("USER#"+bobId);
 }
 @Test void expiredRecruitingTaskIsNotShownInPublicList()throws Exception{
  long id=task();jdbc.update("UPDATE task SET application_deadline=? WHERE id=?",java.sql.Timestamp.valueOf(LocalDateTime.now().minusMinutes(1)),id);
  JsonNode records=req("GET","/api/public/tasks?status=RECRUITING&page=1&size=50",null,null).at("/data/records");
  assertThat(java.util.stream.StreamSupport.stream(records.spliterator(),false).mapToLong(x->x.path("id").asLong())).doesNotContain(id);
 }
 @Test void directAdminStatusReasonsArePersistedAndSeparatedFromReports()throws Exception{
  String admin=login("admin"),alice=login("alice");long adminId=jdbc.queryForObject("SELECT id FROM `user` WHERE username='admin'",Long.class),bobId=jdbc.queryForObject("SELECT id FROM `user` WHERE username='bob'",Long.class);
  assertThat(req("POST","/api/reports",alice,Map.of("targetType","USER","targetId",bobId,"reasonType","ADMIN_USER_STATUS","description","伪造审计记录")).path("code").asInt()).isEqualTo(400);
  assertThat(req("PUT","/api/admin/users/"+bobId+"/status/BANNED",admin,Map.of("reason","重复发布违规内容")).path("code").asInt()).isEqualTo(200);
  Map<String,Object> userAction=jdbc.queryForMap("SELECT * FROM report WHERE reason_type='ADMIN_USER_STATUS' AND target_id=?",bobId);
  assertThat(userAction.get("reporter_id")).isEqualTo(adminId);assertThat(userAction.get("handler_id")).isEqualTo(adminId);assertThat(userAction.get("target_type")).isEqualTo("USER");assertThat(userAction.get("description")).isEqualTo("重复发布违规内容");assertThat(userAction.get("handle_result")).isEqualTo("NORMAL -> BANNED");assertThat(userAction.get("status")).isEqualTo("RESOLVED");assertThat(userAction.get("handled_at")).isNotNull();
  assertThat(req("PUT","/api/admin/skills/3/status/DISABLED",admin,Map.of("reason","课程分类暂停维护")).path("code").asInt()).isEqualTo(200);
  Map<String,Object> skillAction=jdbc.queryForMap("SELECT * FROM report WHERE reason_type='ADMIN_SKILL_STATUS' AND target_id=3");
  assertThat(skillAction.get("target_type")).isEqualTo("SKILL");assertThat(skillAction.get("description")).isEqualTo("课程分类暂停维护");assertThat(skillAction.get("handle_result")).isEqualTo("ENABLED -> DISABLED");
  JsonNode actions=req("GET","/api/admin/actions?page=1&size=10",admin,null).path("data");assertThat(actions.path("total").asInt()).isEqualTo(2);assertThat(actions.path("records").size()).isEqualTo(2);
  JsonNode reports=req("GET","/api/admin/reports?page=1&size=50",admin,null).at("/data/records");assertThat(java.util.stream.StreamSupport.stream(reports.spliterator(),false).map(x->x.path("reasonType").asText())).noneMatch(x->x.startsWith("ADMIN_"));
  assertThat(req("GET","/api/admin/actions",alice,null).path("code").asInt()).isEqualTo(403);
 }
 }
