<template>
  <div class="unityScreen">
    <UnityData :instance="instance" />
    <Tutorial  v-if="!isTutorial" />
    <div id="unity-container" class="unity-desktop" :class="{'unity-mini':!allMap}">
      
      <div class="back-map" v-if="!allMap" @click="goUnityMap()"> 
        <i class="fas fa-long-arrow-alt-left"></i>
        <p class="back-map-text">Back</p>
      </div>

      <canvas id="unity-canvas" ></canvas>
      <div id="unity-loading-bar">
        <div id="unity-logo"></div>
        <div id="unity-progress-bar-empty">
          <div id="unity-progress-bar-full"></div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { mapActions, mapGetters, mapState } from 'vuex';
import UnityData from "./UnityData.vue";
import Tutorial from "@/components/Tutorial.vue";
export default {
  components:{
    UnityData,
    Tutorial,
  },
  data(){
    return{
      instance : null,
      unityInterval : '',
      mapHeight : 0,
      mapWidth : 0,
    }
  },
  computed:{
    ...mapGetters('process',['getInstance','subComplete','allMap','chattingOpen']),
    ...mapState('user',['isLogin','isTutorial']),
    ...mapGetters("education", ["educations"]),
    unityfocus(){
        if(this.$route.name == 'UnityMap') return true;
        else return false;
    }
  },
  watch:{
    unityfocus(val){
      var canvas = document.querySelector("#unity-canvas");
      if(val){
        this.getAllMap(true)
        canvas.style.width = "1280px";
        canvas.style.height = "800px";
        this.instance.SendMessage("KeyManager","FocusCanvas","1");
      }
      else{
        this.getAllMap(false)
        canvas.style.width = "150px";
        canvas.style.height = "100px";
        this.instance.SendMessage('KeyManager','FocusCanvas','0');
      }
    },
    chattingOpen(val){
      if(val){
        this.instance.SendMessage('KeyManager','FocusCanvas','0');
      }
      else{
        this.instance.SendMessage('KeyManager','FocusCanvas','1');
      }
    },
    isLogin(val){
      if(!val) this.instance.SendMessage('KeyManager','FocusCanvas','0');
    }
  },
  created(){
    this.getSubComplete(true)
    if(this.getInstance && this.isTutorial){
      setTimeout(()=>{
        this.getSubComplete(false)
      },3000)
    }else if(!this.getInstance && this.isTutorial){
      setTimeout(()=>{
        this.getSubComplete(false)
      },8000)
    }
    if(!this.getInstance && !this.isLogin){
      this.instance.SendMessage("KeyManager","FocusCanvas","0");
    }
  },
  mounted(){ 
    this.runWebGL()
  },

  methods:{
    ...mapActions('process',['getSubComplete','getAllMap']),
    ...mapActions('education',['getEducations']),
    runWebGL(){
      var buildUrl = "unity/Build";
      var loaderUrl = buildUrl + "/unity.loader.js";
      var config = {
        dataUrl: buildUrl + "/unity.data",
        frameworkUrl: buildUrl + "/unity.framework.js",
        codeUrl: buildUrl + "/unity.wasm",
        streamingAssetsUrl: "StreamingAssets",
        companyName: "MetaCompany",
        productName: "metaone",
        productVersion: "1.0",
      };
      var container = document.querySelector("#unity-container");
      var canvas = document.querySelector("#unity-canvas");
      var loadingBar = document.querySelector("#unity-loading-bar");
      var progressBarFull = document.querySelector("#unity-progress-bar-full");
      var mobileWarning = document.querySelector("#unity-mobile-warning");

      if (/iPhone|iPad|iPod|Android/i.test(navigator.userAgent)) {
        container.className = "unity-mobile";
        config.devicePixelRatio = 1;
        mobileWarning.style.display = "block";
        setTimeout(() => {
          mobileWarning.style.display = "none";
        }, 5000);
      } else {
        canvas.style.width = "1280px";
        canvas.style.height = "800px";
      }
      loadingBar.style.display = "block";
      var script = document.createElement("script");
      script.src = loaderUrl;
      
      script.onload = async () => {
        await window.createUnityInstance(canvas, config, (progress) => {
          progressBarFull.style.width = 100 * progress + "%";
        }).then((unityInstance) => {
          this.instance = unityInstance
          loadingBar.style.display = "none";
          if(this.instance !== undefined) {
            if(this.allMap) this.instance.SendMessage('KeyManager','FocusCanvas','1');
            else this.instance.SendMessage('KeyManager','FocusCanvas','0');

            this.$store.commit("process/SET_UNITY_INSTANCE",true);
          }
        }).catch((message) => {
          alert(message);
        });
      };
      document.body.appendChild(script);

      if(this.allMap){
        canvas.style.width = "1280px";
        canvas.style.height = "800px";
      }else{
        canvas.style.width = "150px";
        canvas.style.height = "100px";
      }
    },
    goUnityMap(){
        this.$router.push({name: 'UnityMap'})
    },
  }
}
</script>

<style>

</style>