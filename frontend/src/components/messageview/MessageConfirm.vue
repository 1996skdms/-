// 받은 메세지 확인용

<template>
  <section class="MessageForm">
    <div class="m_title">
      <p>내용</p>
    </div>
    <div class="m-contain">
    </div>
    <div class="send_card">
      <div class="row message_com m_title mt-5">
        <div class="col-md-2 pt-1 px-0">{{titles[0]}}</div>
        <div class="col-md-10">
            <input type="text" readonly class="form-control" v-model="valid_title">
        </div>
      </div>
      <div class="row message_com m_content mt-4">
        <label for="ContentArea" class="content-label">{{titles[1]}}</label>
        <textarea readonly class="form-control" id="ContentArea" rows="10"
          v-model="valid_content"></textarea>
      </div>
      <div v-if="isyour"  class="row message_com mt-4 m_answer justify-content-end">
        <button @click="sendMessage()" :disabled="valid_title == ''">답장하기</button>
      </div>
    </div>
  </section>
</template>

<script>
import { mapGetters, mapActions, mapState } from "vuex";

export default {
  
  data(){
    return{
      titles: ["제목", "내용"],
      empty: '',
    }
  },
  computed:{

    ...mapGetters("message", ["selectmessage","sendmode"]),
    ...mapState("user", ["nickname"]),
    
    valid_title(){
      if(this.selectmessage) 
        return this.selectmessage.title
      else return ''
    },
    valid_content(){
      if(this.selectmessage) 
        return this.selectmessage.content
      else return ''
    },
    isyour(){
      return this.selectmessage.nickname!=this.nickname
    }
  },
  methods:{
    ...mapActions("message", ["getReceiver","getSendmode"]),
    sendMessage(){
      this.getReceiver(this.selectmessage.nickname)
      this.getSendmode(true)
    }
  }
}
</script>

<style>

</style>