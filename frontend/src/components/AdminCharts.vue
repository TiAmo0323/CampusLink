<script setup>
import { onBeforeUnmount,onMounted,watch,ref } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart,LineChart } from 'echarts/charts'
import { GridComponent,LegendComponent,TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
echarts.use([BarChart,LineChart,GridComponent,LegendComponent,TooltipComponent,CanvasRenderer])
const props=defineProps({stats:{type:Object,default:()=>({})}}),trendEl=ref(),categoryEl=ref()
let charts=[]
function render(){if(!trendEl.value)return;charts.forEach(c=>c.dispose());charts=[echarts.init(trendEl.value),echarts.init(categoryEl.value)];const dates=(props.stats.registrationTrend||[]).map(x=>x.date);charts[0].setOption({tooltip:{trigger:'axis'},legend:{data:['新增用户','发布任务']},grid:{left:40,right:18,bottom:30,top:42},xAxis:{type:'category',data:dates},yAxis:{type:'value',minInterval:1},series:[{name:'新增用户',type:'line',smooth:true,data:(props.stats.registrationTrend||[]).map(x=>x.value),itemStyle:{color:'#2f7d68'}},{name:'发布任务',type:'line',smooth:true,data:(props.stats.taskTrend||[]).map(x=>x.value),itemStyle:{color:'#d89b45'}}]});const categories=props.stats.categoryDistribution||[];charts[1].setOption({tooltip:{trigger:'axis'},grid:{left:78,right:18,bottom:32,top:24},xAxis:{type:'category',data:categories.map(x=>x.name),axisLabel:{interval:0,rotate:20}},yAxis:{type:'value',minInterval:1},series:[{name:'任务数量',type:'bar',data:categories.map(x=>x.value),itemStyle:{color:'#79a89a',borderRadius:[5,5,0,0]}}]})}
function resize(){charts.forEach(c=>c.resize())}
watch(()=>props.stats,render,{deep:true});onMounted(()=>{render();window.addEventListener('resize',resize)});onBeforeUnmount(()=>{window.removeEventListener('resize',resize);charts.forEach(c=>c.dispose())})
</script>
<template><section class="chart-grid"><div class="surface panel"><h3>近 7 日新增用户与任务</h3><div ref="trendEl" class="chart"/></div><div class="surface panel"><h3>任务分类统计</h3><div ref="categoryEl" class="chart"/></div></section></template>
<style scoped>.chart-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:18px;margin-bottom:20px}.chart{height:260px}@media(max-width:720px){.chart-grid{grid-template-columns:1fr}.chart{height:230px}}</style>
