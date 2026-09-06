package com.campuslink.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campuslink.common.BusinessException;
import com.campuslink.domain.*;
import com.campuslink.dto.Requests;
import com.campuslink.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class SkillService {
    private final SkillMapper skillMapper;private final UserSkillMapper offerMapper;private final UserSkillNeedMapper needMapper;private final UserMapper userMapper;private final ReviewMapper reviewMapper;private final TaskOrderMapper orderMapper;private final SkillExchangeMapper exchangeMapper;private final AccountService accountService;
    @Value("${campuslink.match.skill-weight:0.5}") private double skillWeight;
    @Value("${campuslink.match.credit-weight:0.2}") private double creditWeight;
    @Value("${campuslink.match.rating-weight:0.2}") private double ratingWeight;
    @Value("${campuslink.match.activity-weight:0.1}") private double activityWeight;
    public Map<String,Object> skills(int page,int size){com.baomidou.mybatisplus.extension.plugins.pagination.Page<Skill> p=skillMapper.selectPage(com.campuslink.common.Pages.request(page,size),new LambdaQueryWrapper<Skill>().eq(Skill::getStatus,States.Skill.ENABLED.name()).orderByAsc(Skill::getId));return com.campuslink.common.Pages.of(p,p.getRecords());}
    public Map<String,Object> profile(Long uid,int offerPage,int offerSize,int needPage,int needSize){
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<UserSkill> offers=offerMapper.selectPage(com.campuslink.common.Pages.request(offerPage,offerSize),new LambdaQueryWrapper<UserSkill>().eq(UserSkill::getUserId,uid).orderByAsc(UserSkill::getId));
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<UserSkillNeed> needs=needMapper.selectPage(com.campuslink.common.Pages.request(needPage,needSize),new LambdaQueryWrapper<UserSkillNeed>().eq(UserSkillNeed::getUserId,uid).orderByAsc(UserSkillNeed::getId));
        Set<Long> ids=new HashSet<>();offers.getRecords().forEach(o->ids.add(o.getSkillId()));needs.getRecords().forEach(n->ids.add(n.getSkillId()));
        Map<Long,Skill> skills=ids.isEmpty()?Map.of():skillMapper.selectByIds(ids).stream().collect(Collectors.toMap(Skill::getId,s->s));
        Map<String,Object> result=new LinkedHashMap<>();result.put("offers",offers.getRecords().stream().map(o->withSkill(o,skills.get(o.getSkillId()))).toList());result.put("offersTotal",offers.getTotal());result.put("offerPage",offers.getCurrent());result.put("offerSize",offers.getSize());result.put("needs",needs.getRecords().stream().map(n->withSkill(n,skills.get(n.getSkillId()))).toList());result.put("needsTotal",needs.getTotal());result.put("needPage",needs.getCurrent());result.put("needSize",needs.getSize());result.put("total",Math.max(offers.getTotal(),needs.getTotal()));return result;
    }
    public Map<String,Object> profile(Long uid,int page,int size){return profile(uid,page,size,page,size);}
    @Transactional public void saveOffer(Long userId,Requests.SkillOffer dto){requiredSkill(dto.skillId());UserSkill o=offerMapper.selectOne(new LambdaQueryWrapper<UserSkill>().eq(UserSkill::getUserId,userId).eq(UserSkill::getSkillId,dto.skillId()));if(o==null){o=new UserSkill();o.setUserId(userId);o.setSkillId(dto.skillId());o.setCreatedAt(LocalDateTime.now());}o.setProficiency(dto.proficiency());o.setDescription(dto.description());o.setAvailableMode(dto.availableMode());o.setAvailableTime(dto.availableTime());if(o.getId()==null)offerMapper.insert(o);else offerMapper.updateById(o);}
    @Transactional public void saveNeed(Long userId,Requests.SkillNeed dto){requiredSkill(dto.skillId());UserSkillNeed n=needMapper.selectOne(new LambdaQueryWrapper<UserSkillNeed>().eq(UserSkillNeed::getUserId,userId).eq(UserSkillNeed::getSkillId,dto.skillId()));if(n==null){n=new UserSkillNeed();n.setUserId(userId);n.setSkillId(dto.skillId());n.setCreatedAt(LocalDateTime.now());}n.setPriority(dto.priority()==null?1:dto.priority());n.setDescription(dto.description());n.setPreferredMode(dto.preferredMode());if(n.getId()==null)needMapper.insert(n);else needMapper.updateById(n);}
    public void deleteOffer(Long userId,Long id){UserSkill o=offerMapper.selectById(id);if(o==null||!userId.equals(o.getUserId()))throw new BusinessException("技能供给不存在");offerMapper.deleteById(id);} public void deleteNeed(Long userId,Long id){UserSkillNeed n=needMapper.selectById(id);if(n==null||!userId.equals(n.getUserId()))throw new BusinessException("技能需求不存在");needMapper.deleteById(id);}
    public Map<String,Object> matches(Long currentUserId,int page,int size){
        Set<Long> enabled=skillMapper.selectList(new LambdaQueryWrapper<Skill>().eq(Skill::getStatus,States.Skill.ENABLED.name())).stream().map(Skill::getId).collect(Collectors.toSet());
        Set<Long> needs=needMapper.selectList(new LambdaQueryWrapper<UserSkillNeed>().eq(UserSkillNeed::getUserId,currentUserId)).stream().map(UserSkillNeed::getSkillId).filter(enabled::contains).collect(Collectors.toSet());
        if(needs.isEmpty())return com.campuslink.common.Pages.slice(List.of(),page,size);
        Set<Long> offers=offerMapper.selectList(new LambdaQueryWrapper<UserSkill>().eq(UserSkill::getUserId,currentUserId)).stream().map(UserSkill::getSkillId).filter(enabled::contains).collect(Collectors.toSet());
        var candidateOffers=offerMapper.selectList(new LambdaQueryWrapper<UserSkill>().in(UserSkill::getSkillId,needs).ne(UserSkill::getUserId,currentUserId));
        Set<Long> ids=candidateOffers.stream().map(UserSkill::getUserId).collect(Collectors.toSet());
        if(ids.isEmpty())return com.campuslink.common.Pages.slice(List.of(),page,size);
        var users=userMapper.selectList(new LambdaQueryWrapper<User>().in(User::getId,ids).eq(User::getStatus,States.Account.NORMAL.name()).eq(User::getRole,States.Role.STUDENT.name()));
        Map<Long,Set<Long>> supplied=new HashMap<>(),wanted=new HashMap<>();
        for(var o:candidateOffers)supplied.computeIfAbsent(o.getUserId(),k->new HashSet<>()).add(o.getSkillId());
        for(var n:needMapper.selectList(new LambdaQueryWrapper<UserSkillNeed>().in(UserSkillNeed::getUserId,ids)))wanted.computeIfAbsent(n.getUserId(),k->new HashSet<>()).add(n.getSkillId());
        Map<Long,Double> ratings=reviewMapper.averageRatings().stream().collect(Collectors.toMap(MapperRows.AggregateValue::getId,MapperRows.AggregateValue::getMetricValue));
        Map<Long,Long> counts=orderMapper.completedCountsSince(States.Task.COMPLETED.name(),LocalDateTime.now().minusDays(30)).stream().collect(Collectors.toMap(MapperRows.AggregateValue::getId,row->row.getMetricValue().longValue()));
        Map<Long,Skill> skillMap=skillMapper.selectList(null).stream().collect(Collectors.toMap(Skill::getId,Function.identity()));
        List<Map<String,Object>> result=new ArrayList<>();
        for(var u:users){Set<Long> intersection=supplied.get(u.getId());long completed=counts.getOrDefault(u.getId(),0L);double score=intersection.size()/(double)needs.size()*skillWeight+u.getCreditScore()/100.0*creditWeight+ratings.getOrDefault(u.getId(),3.0)/5*ratingWeight+Math.min(completed/10.0,1)*activityWeight;
            boolean mutual=wanted.getOrDefault(u.getId(),Set.of()).stream().anyMatch(offers::contains);
            Map<String,Object> item=new LinkedHashMap<>();item.put("user",AuthService.publicUserView(u));item.put("matchedSkills",intersection.stream().sorted().map(skillMap::get).toList());item.put("score",Math.round(score*100));item.put("mutualMatch",mutual);item.put("reason","覆盖 "+intersection.size()+" 项需求，信用 "+u.getCreditScore()+"，近30日完成 "+completed+" 单");result.add(item);
        }
        result.sort(Comparator.<Map<String,Object>>comparingDouble(m->-((Number)m.get("score")).doubleValue()).thenComparing(m->!(Boolean)m.get("mutualMatch")).thenComparingLong(m->((Number)((Map<?,?>)m.get("user")).get("id")).longValue()));
        return com.campuslink.common.Pages.slice(result,page,size);
    }
    @Transactional public SkillExchange createExchange(Long requesterId,Requests.Exchange dto){if(requesterId.equals(dto.providerId()))throw new BusinessException("不能向自己发起互助");if(!States.Account.NORMAL.name().equals(accountService.requiredUser(requesterId).getStatus())||!States.Account.NORMAL.name().equals(accountService.requiredUser(dto.providerId()).getStatus()))throw new BusinessException("互助双方账户必须可用");requiredSkill(dto.requestSkillId());if(needMapper.selectCount(new LambdaQueryWrapper<UserSkillNeed>().eq(UserSkillNeed::getUserId,requesterId).eq(UserSkillNeed::getSkillId,dto.requestSkillId()))==0)throw new BusinessException("请先将请求技能加入本人需求");if(offerMapper.selectCount(new LambdaQueryWrapper<UserSkill>().eq(UserSkill::getUserId,dto.providerId()).eq(UserSkill::getSkillId,dto.requestSkillId()))==0)throw new BusinessException("对方未提供该技能");if(dto.exchangeSkillId()!=null){requiredSkill(dto.exchangeSkillId());if(offerMapper.selectCount(new LambdaQueryWrapper<UserSkill>().eq(UserSkill::getUserId,requesterId).eq(UserSkill::getSkillId,dto.exchangeSkillId()))==0)throw new BusinessException("本人未提供交换技能");}SkillExchange e=new SkillExchange();e.setRequesterId(requesterId);e.setProviderId(dto.providerId());e.setRequestSkillId(dto.requestSkillId());e.setExchangeSkillId(dto.exchangeSkillId());e.setMessage(dto.message());e.setScheduledTime(dto.scheduledTime());e.setStatus(com.campuslink.domain.States.Exchange.PENDING.name());e.setCreatedAt(LocalDateTime.now());exchangeMapper.insert(e);accountService.notify(dto.providerId(),"EXCHANGE_REQUEST","收到技能互助申请",accountService.requiredUser(requesterId).getNickname()+"向你发起技能互助",e.getId());return e;}
    public Map<String,Object> exchanges(Long userId,int page,int size){
        var query=new LambdaQueryWrapper<SkillExchange>().and(q->q.eq(SkillExchange::getRequesterId,userId).or().eq(SkillExchange::getProviderId,userId)).orderByDesc(SkillExchange::getId);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<SkillExchange> result=exchangeMapper.selectPage(com.campuslink.common.Pages.request(page,size),query);
        Set<Long> userIds=new HashSet<>(),skillIds=new HashSet<>(),exchangeIds=new HashSet<>();
        for(SkillExchange exchange:result.getRecords()){userIds.add(exchange.getRequesterId());userIds.add(exchange.getProviderId());skillIds.add(exchange.getRequestSkillId());if(exchange.getExchangeSkillId()!=null)skillIds.add(exchange.getExchangeSkillId());exchangeIds.add(exchange.getId());}
        Map<Long,User> users=userIds.isEmpty()?Map.of():userMapper.selectByIds(userIds).stream().collect(Collectors.toMap(User::getId,Function.identity()));
        Map<Long,Skill> skills=skillIds.isEmpty()?Map.of():skillMapper.selectByIds(skillIds).stream().collect(Collectors.toMap(Skill::getId,Function.identity()));
        Set<Long> reviewed=exchangeIds.isEmpty()?Set.of():reviewMapper.selectList(new LambdaQueryWrapper<Review>().eq(Review::getBusinessType,"SKILL_EXCHANGE").eq(Review::getReviewerId,userId).in(Review::getBusinessId,exchangeIds)).stream().map(Review::getBusinessId).collect(Collectors.toSet());
        List<Map<String,Object>> records=result.getRecords().stream().map(exchange->{Map<String,Object> view=new LinkedHashMap<>();view.put("exchange",exchange);view.put("requester",AuthService.publicUserView(users.get(exchange.getRequesterId())));view.put("provider",AuthService.publicUserView(users.get(exchange.getProviderId())));view.put("requestSkill",skills.get(exchange.getRequestSkillId()));if(exchange.getExchangeSkillId()!=null)view.put("exchangeSkill",skills.get(exchange.getExchangeSkillId()));view.put("reviewedByMe",reviewed.contains(exchange.getId()));return view;}).toList();
        return com.campuslink.common.Pages.of(result,records);
    }
    @Transactional public SkillExchange changeExchange(Long id,Long uid,String action){
        SkillExchange e=exchangeMapper.lockById(id);if(e==null)throw new BusinessException("互助申请不存在");
        boolean provider=uid.equals(e.getProviderId()),requester=uid.equals(e.getRequesterId());if(!provider&&!requester)throw new BusinessException(403,"仅互助双方可以操作");
        switch(action){
            case "accept","reject"->{if(!provider||!com.campuslink.domain.States.Exchange.PENDING.name().equals(e.getStatus()))throw new BusinessException("当前不能接受或拒绝");if("accept".equals(action))validateExchange(e);e.setStatus("accept".equals(action)?com.campuslink.domain.States.Exchange.ACCEPTED.name():com.campuslink.domain.States.Exchange.REJECTED.name());}
            case "start"->{if(!requester||!com.campuslink.domain.States.Exchange.ACCEPTED.name().equals(e.getStatus()))throw new BusinessException("仅发起者可开始已接受互助");e.setStatus(com.campuslink.domain.States.Exchange.IN_PROGRESS.name());}
            case "cancel"->{if(!Set.of(com.campuslink.domain.States.Exchange.ACCEPTED.name(),com.campuslink.domain.States.Exchange.IN_PROGRESS.name()).contains(e.getStatus()))throw new BusinessException("当前不能取消");e.setStatus(com.campuslink.domain.States.Exchange.CANCELLED.name());}
            case "complete"->{if(!com.campuslink.domain.States.Exchange.IN_PROGRESS.name().equals(e.getStatus()))throw new BusinessException("当前不能完成");if(provider){if(e.getProviderCompletedAt()==null)e.setProviderCompletedAt(LocalDateTime.now());}else{if(e.getProviderCompletedAt()==null)throw new BusinessException("请等待提供方标记完成");e.setStatus(com.campuslink.domain.States.Exchange.COMPLETED.name());e.setCompletedAt(LocalDateTime.now());}}
            default->throw new BusinessException("不支持的操作");
        }
        exchangeMapper.updateById(e);accountService.notify(provider?e.getRequesterId():e.getProviderId(),"EXCHANGE_REQUEST","技能互助状态更新",action,e.getId());return e;
    }
    private Skill requiredSkill(Long id){Skill s=skillMapper.selectById(id);if(s==null||!States.Skill.ENABLED.name().equals(s.getStatus()))throw new BusinessException("技能分类不存在或已停用");return s;} private Map<String,Object> withSkill(Object record,Skill skill){Map<String,Object>m=new LinkedHashMap<>();m.put("record",record);m.put("skill",skill);return m;}
    private void validateExchange(SkillExchange e){if(!States.Account.NORMAL.name().equals(accountService.requiredUser(e.getRequesterId()).getStatus())||!States.Account.NORMAL.name().equals(accountService.requiredUser(e.getProviderId()).getStatus()))throw new BusinessException("互助双方账户必须可用");requiredSkill(e.getRequestSkillId());if(needMapper.selectCount(new LambdaQueryWrapper<UserSkillNeed>().eq(UserSkillNeed::getUserId,e.getRequesterId()).eq(UserSkillNeed::getSkillId,e.getRequestSkillId()))==0)throw new BusinessException("发起者已不再需要该技能");if(offerMapper.selectCount(new LambdaQueryWrapper<UserSkill>().eq(UserSkill::getUserId,e.getProviderId()).eq(UserSkill::getSkillId,e.getRequestSkillId()))==0)throw new BusinessException("提供方已不再提供该技能");if(e.getExchangeSkillId()!=null){requiredSkill(e.getExchangeSkillId());if(offerMapper.selectCount(new LambdaQueryWrapper<UserSkill>().eq(UserSkill::getUserId,e.getRequesterId()).eq(UserSkill::getSkillId,e.getExchangeSkillId()))==0)throw new BusinessException("发起者已不再提供交换技能");}}
}
