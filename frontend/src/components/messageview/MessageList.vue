// 메시지 리스트 확인

<template>
  <section class="MessageList">
    <div class="m_title">
        <p v-if="allmode">받은 쪽지함</p>
        <p v-else>쪽지 내역</p>
    </div>
    <div class="m-contain">
    </div>
    <div class="m_card">
      
      <button v-if="!allmode" class="back-btn" @click="getAllmode(true)">
        <i class="fas fa-arrow-left"></i>
      </button>
      <transition v-if="allmode" name="slide-fade1" mode="out-in">
        <!-- 내 메세지 내역 -->
        <ul key=1 class="list-group mymess overflow-auto pt-3 mt-2">
          <MyMessageCard  v-for="(message, index) in mymessages" :key="index" :message="message"
          @click.native="readAndGet(message)" :class="{'listSelected': selectMess(message)}"/>
        </ul>
      </transition>
      <transition v-else name="slide-fade2" mode="out-in">
        <!-- 나와 상대방 1대1 -->
        <ul key=2 class="list-group onesmess overflow-auto pt-3 mt-2">
          <OnesMessageCard  v-for="(message, index) in onebyonemessages" :key="index" :message="message"
          @click.native="readAndGet(message)" :class="{'listSelected': selectMess(message)}"/>
        </ul>
      </transition>
    </div>
  </section>
</template>

<script>
import messageAPI from "@/api/message.js";
import MyMessageCard from "@/components/messageview/MyMessageCard.vue"
import OnesMessageCard from "@/components/messageview/OnesMessageCard.vue"
import { mapActions, mapGetters, mapState } from "vuex";
export default {
    components:{
        MyMessageCard,OnesMessageCard
    },
    data(){
        return{
            
        }
    },
    computed:{
      ...mapGetters("message",["mymessages","onebyonemessages","allmode","selectmessage"]),
      ...mapState("user",["nickname"])
    },
    methods:{
      ...mapActions('message', ['getMessage','getSendmode','getAllmode','getMyMessages','getOnebyOneMessages']),
      async readAndGet(mm){
        if(!mm.read && this.nickname!=mm.nickname){
          await this.reading(mm.id)
          if(this.allmode) await this.getMyMessages()
          else await this.getOnebyOneMessages(mm.nickname)
        }
 
        await this.getMessage(mm)
        await this.getSendmode(false)
      },

      async reading(msgId){
        await messageAPI
          .readMessage(msgId)
          .then((res) => {
          console.log(res.data)
        })
        .catch((error) => {
          alert("x");
          console.log(error);
        });
        
      },

      selectMess(val){
        if (val.id==this.selectmessage.id) return true
        else false
      }
    },
    created(){
      this.getMyMessages()
    }
}
</script>

<style>

</style>