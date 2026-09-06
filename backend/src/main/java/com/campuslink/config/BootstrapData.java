package com.campuslink.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campuslink.domain.*;
import com.campuslink.mapper.*;
import com.campuslink.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Component @RequiredArgsConstructor @ConditionalOnProperty(prefix="campuslink",name="seed-demo-data",havingValue="true",matchIfMissing=true)
public class BootstrapData implements CommandLineRunner {
    private final UserMapper userMapper;private final SkillMapper skillMapper;private final UserSkillMapper offerMapper;private final UserSkillNeedMapper needMapper;private final TaskMapper taskMapper;private final PointTransactionMapper pointMapper;private final AuthService authService;
    @Override @Transactional public void run(String... args){if(userMapper.selectCount(null)>0)return;LocalDateTime now=LocalDateTime.now();User admin=user("admin","系统管理员","20260000","admin@campus.edu.cn",States.Role.ADMIN.name(),100,1000,now,"admin123");User alice=user("alice","林晓","20260001","alice@campus.edu.cn",States.Role.STUDENT.name(),96,500,now,"demo123");User bob=user("bob","陈宇","20260002","bob@campus.edu.cn",States.Role.STUDENT.name(),92,300,now,"demo123");
        List<String[]> defs=List.of(new String[]{"Java编程","编程开发"},new String[]{"Vue前端","编程开发"},new String[]{"摄影修图","创意设计"},new String[]{"高等数学","课程辅导"},new String[]{"英语口语","语言学习"},new String[]{"PPT设计","创意设计"},new String[]{"羽毛球","体育运动"},new String[]{"吉他","音乐艺术"});for(String[] d:defs){Skill s=new Skill();s.setName(d[0]);s.setCategory(d[1]);s.setStatus(States.Skill.ENABLED.name());s.setCreatedAt(now);skillMapper.insert(s);}List<Skill> skills=skillMapper.selectList(null);offer(alice.getId(),skills.get(0).getId(),"ADVANCED",now);offer(alice.getId(),skills.get(1).getId(),"ADVANCED",now);need(alice.getId(),skills.get(2).getId(),2,now);offer(bob.getId(),skills.get(2).getId(),"INTERMEDIATE",now);offer(bob.getId(),skills.get(4).getId(),"ADVANCED",now);need(bob.getId(),skills.get(0).getId(),3,now);
        CampusTask t=new CampusTask();t.setPublisherId(alice.getId());t.setTitle("帮忙拍摄社团招新照片");t.setCategory("校园活动");t.setDescription("需要一位会摄影的同学，在社团招新现场协助拍摄约一小时，器材可自带也可使用社团相机。");t.setLocation("大学生活动中心");t.setTaskTime(now.plusDays(3));t.setApplicationDeadline(now.plusDays(2));t.setRewardPoints(40);t.setMinCreditScore(70);t.setStatus(States.Task.RECRUITING.name());t.setVersion(0);t.setCreatedAt(now);t.setUpdatedAt(now);taskMapper.insert(t);alice.setAvailablePoints(460);alice.setFrozenPoints(40);userMapper.updateById(alice);PointTransaction tx=new PointTransaction();tx.setUserId(alice.getId());tx.setBusinessType("TASK_FREEZE");tx.setBusinessId(t.getId());tx.setChangeAmount(-40);tx.setBeforeFrozen(0);tx.setAfterFrozen(40);tx.setBeforeBalance(500);tx.setAfterBalance(460);tx.setRemark("示例任务冻结积分");tx.setCreatedAt(now);pointMapper.insert(tx);
    }
    private User user(String username,String nickname,String no,String email,String role,int credit,int points,LocalDateTime now,String password){User u=new User();u.setUsername(username);u.setNickname(nickname);u.setStudentNo(no);u.setEmail(email);u.setRole(role);u.setCreditScore(credit);u.setAvailablePoints(points);u.setFrozenPoints(0);u.setStatus(States.Account.NORMAL.name());u.setPasswordHash(authService.encode(password));u.setCreatedAt(now);u.setUpdatedAt(now);userMapper.insert(u);return u;}
    private void offer(Long uid,Long sid,String level,LocalDateTime now){UserSkill o=new UserSkill();o.setUserId(uid);o.setSkillId(sid);o.setProficiency(level);o.setAvailableMode("BOTH");o.setCreatedAt(now);offerMapper.insert(o);}private void need(Long uid,Long sid,int priority,LocalDateTime now){UserSkillNeed n=new UserSkillNeed();n.setUserId(uid);n.setSkillId(sid);n.setPriority(priority);n.setPreferredMode("BOTH");n.setCreatedAt(now);needMapper.insert(n);}
}
