<template>
  <div class="min-h-screen transition-colors duration-300 ease-in-out flex flex-col gap-4 p-4 font-sans bg-gray-50 text-gray-900 dark:bg-[#121212] dark:text-gray-200">
    
    <!-- 1. 顶部标题栏 & 导航 (固定置顶) -->
    <div class="sticky top-0 z-40 flex items-center justify-between px-2 py-2 -mx-4 -mt-4 mb-2 bg-gray-50 dark:bg-[#121212] border-b border-gray-200 dark:border-white/5">
      <!-- 左侧: 对话历史按钮 -->
      <button 
        @click="showChatHistory = true"
        class="flex items-center justify-center w-9 h-9 rounded-lg transition-all active:scale-95 bg-white text-gray-600 border border-gray-200 hover:bg-gray-100 dark:bg-white/5 dark:text-gray-300 dark:border-white/10 dark:hover:bg-white/10"
        title="对话历史"
      >
        <svg xmlns="http://www.w3.org/2000/svg" class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
      </button>

      <!-- 中间: 标题 -->
      <div class="flex items-center gap-2">
        <div class="w-1.5 h-5 rounded-full shadow-sm bg-[#00C853] shadow-[0_0_8px_rgba(0,200,83,0.5)]"></div>
        <h1 class="text-xl font-bold tracking-wide text-gray-800 dark:text-white">AutoGLM</h1>
      </div>
      
      <!-- 右侧: 设置按钮 (带权限状态红点) -->
      <button 
        @click="showSettings = true"
        class="relative flex items-center justify-center w-9 h-9 rounded-lg transition-all active:scale-95 bg-white text-gray-600 border border-gray-200 hover:bg-gray-100 dark:bg-white/5 dark:text-gray-300 dark:border-white/10 dark:hover:bg-white/10"
        title="设置"
      >
        <svg xmlns="http://www.w3.org/2000/svg" class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z"/>
          <circle cx="12" cy="12" r="3"/>
        </svg>
        <!-- 红点: 权限未完全授予时显示 -->
        <span 
          v-if="needsPermissionSetup" 
          class="absolute -top-1 -right-1 w-3 h-3 rounded-full bg-red-500 border-2 border-white dark:border-[#121212]"
        ></span>
      </button>
    </div>

    <!-- 主界面 -->
    <div v-if="!showSettings" class="flex flex-col flex-1 min-h-0">
            
            <!-- 对话消息区域 -->
            <div 
              ref="logContainer" 
              class="flex-1 overflow-y-auto p-4 space-y-3 scrollbar-thin"
            >
              <!-- 空状态 -->
              <div v-if="logs.length === 0 && !isRunning" class="h-full flex flex-col items-center justify-center text-gray-400 dark:text-gray-600 space-y-4 py-12">
                <div class="w-20 h-20 rounded-full bg-gray-100 dark:bg-white/5 flex items-center justify-center">
                  <svg xmlns="http://www.w3.org/2000/svg" class="w-10 h-10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
                </div>
                <div class="text-center">
                  <div class="font-medium text-base text-gray-600 dark:text-gray-400">你好！我是 AutoGLM</div>
                  <div class="text-sm mt-1">输入任务，我来帮你执行</div>
                </div>
              </div>
              
              <!-- 对话轮次列表 -->
              <template v-for="turn in conversationTurns" :key="turn.id">
                <!-- 用户消息 -->
                <div class="flex justify-end">
                  <div class="max-w-[80%] bg-[#00C853] text-white rounded-2xl rounded-br-sm px-4 py-2.5 shadow-sm group relative">
                    <div class="text-sm">{{ turn.userTask }}</div>
                    
                    <!-- 用户消息操作栏 -->
                    <div class="flex items-center justify-end gap-3 mt-1 pt-1 border-t border-white/20">
                        <button 
                            @click="copyMessage(turn.userTask)"
                            class="text-white/60 hover:text-white transition-colors"
                            title="复制"
                        >
                           <svg xmlns="http://www.w3.org/2000/svg" class="w-3 h-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>
                        </button>
                        <div class="text-[10px] text-white/60">{{ formatTime(turn.userTimestamp) }}</div>
                    </div>
                  </div>
                </div>
                
                <!-- AI 响应 (独立气泡) -->
                <div v-if="turn.aiResponses.length > 0" class="flex flex-col gap-2 mt-2">
                  <div v-for="(resp, idx) in turn.aiResponses" :key="idx" class="flex justify-start">
                    <div class="max-w-[85%] rounded-2xl rounded-bl-sm px-4 py-3 shadow-sm text-sm"
                         :class="{
                           'bg-white dark:bg-[#1E1E1E] text-gray-700 dark:text-gray-300 border border-gray-100 dark:border-white/5': !resp.message.includes('思考') && !resp.type.startsWith('ACTION') && resp.type !== 'ERROR',
                           'bg-purple-50 dark:bg-purple-900/20 text-purple-700 dark:text-purple-300 border border-purple-100 dark:border-purple-800': resp.message.includes('思考') || resp.message.includes('分析'),
                           'bg-blue-50 dark:bg-blue-900/20 text-blue-700 dark:text-blue-300 border border-blue-100 dark:border-blue-800': resp.type === 'ACTION' || resp.message.startsWith('执行'),
                           'bg-red-50 dark:bg-red-900/20 text-red-700 dark:text-red-300 border border-red-100 dark:border-red-800': resp.type === 'ERROR'
                         }">
                      <div class="flex items-start gap-2">
                        <!-- 图标 -->
                        <span v-if="resp.message.includes('思考') || resp.message.includes('分析')">💭</span>
                        <span v-else-if="resp.type === 'ACTION' || resp.message.startsWith('执行')">🎯</span>
                        <span v-else-if="resp.type === 'ERROR'">❌</span>
                        <span v-else-if="resp.type === 'WARNING'">⚠️</span>
                        
                        <!-- 消息内容 -->
                        <div class="whitespace-pre-wrap flex-1">{{ resp.message }}</div>
                      </div>
                      
                      <!-- 底部操作栏 -->
                      <div class="flex items-center justify-end gap-3 mt-2 pt-2 border-t border-black/5 dark:border-white/5 opacity-80">
                         <!-- 复制 -->
                         <button 
                            @click="copyMessage(resp.message)"
                            class="text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 transition-colors"
                            title="复制"
                         >
                           <svg xmlns="http://www.w3.org/2000/svg" class="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>
                         </button>
                         <!-- 删除 -->
                         <button 
                            @click="deleteMessage(resp.id)"
                            class="text-gray-400 hover:text-red-500 transition-colors"
                            title="删除"
                         >
                           <svg xmlns="http://www.w3.org/2000/svg" class="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
                         </button>
                         <!-- 时间戳 -->
                         <div class="text-[10px] opacity-40">{{ formatTime(resp.timestamp) }}</div>
                      </div>
                    </div>
                  </div>
                  
                  <!-- 状态标记 (仅在最后一条显示) -->
                  <div v-if="turn.isComplete" class="ml-2 mb-4 text-[10px] text-green-500 flex items-center gap-1">
                    ✓ 本轮对话结束
                  </div>
                </div>
              </template>
              
              <!-- 正在执行指示器 -->
              <div v-if="isRunning" class="flex justify-start">
                <div class="bg-white dark:bg-[#1E1E1E] border border-gray-200 dark:border-white/10 rounded-2xl px-4 py-3 shadow-sm">
                  <div class="flex items-center gap-2">
                    <div class="flex gap-1">
                      <span class="w-2 h-2 rounded-full bg-[#00C853] animate-bounce" style="animation-delay: 0ms"></span>
                      <span class="w-2 h-2 rounded-full bg-[#00C853] animate-bounce" style="animation-delay: 150ms"></span>
                      <span class="w-2 h-2 rounded-full bg-[#00C853] animate-bounce" style="animation-delay: 300ms"></span>
                    </div>
                    <span class="text-xs text-gray-500">执行中...</span>
                  </div>
                </div>
              </div>
            </div>
            
            <!-- 底部输入区域 -->
            <div class="border-t border-gray-200 dark:border-white/10 bg-white dark:bg-[#1E1E1E] p-3">
              <!-- 执行中控制栏 -->
              <div v-if="isRunning" class="flex items-center justify-between mb-3 px-2">
                <div class="flex items-center gap-2 text-sm text-gray-600 dark:text-gray-400">
                  <span class="w-2 h-2 rounded-full bg-green-500 animate-pulse"></span>
                  任务执行中
                </div>
                <div class="flex gap-2">
                  <button 
                    @click="togglePause"
                    class="px-4 py-1.5 text-sm font-medium rounded-lg transition-colors"
                    :class="isPaused ? 'bg-green-100 text-green-700 dark:bg-green-500/20 dark:text-green-400' : 'bg-amber-100 text-amber-700 dark:bg-amber-500/20 dark:text-amber-400'"
                  >
                    {{ isPaused ? '▶ 继续' : '⏸ 暂停' }}
                  </button>
                  <button 
                    @click="stopTask"
                    class="px-4 py-1.5 text-sm font-medium rounded-lg bg-red-100 text-red-700 dark:bg-red-500/20 dark:text-red-400"
                  >
                    ⏹ 停止
                  </button>
                </div>
              </div>
              
              <!-- 输入栏 -->
              <div class="flex items-center gap-2">
                <!-- 历史按钮 -->
                <div class="relative" v-if="commandHistory.length > 0 && !isRunning">
                  <button 
                    @click="showHistoryDropdown = !showHistoryDropdown"
                    class="w-10 h-10 rounded-xl flex items-center justify-center transition-colors bg-gray-100 dark:bg-white/5 text-gray-500 dark:text-gray-400 hover:bg-gray-200 dark:hover:bg-white/10"
                  >
                    <svg xmlns="http://www.w3.org/2000/svg" class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
                  </button>
                  <!-- 历史下拉菜单 -->
                  <div v-if="showHistoryDropdown" class="absolute bottom-12 left-0 w-64 bg-white dark:bg-[#252525] border border-gray-200 dark:border-white/10 rounded-xl shadow-lg overflow-hidden z-10">
                    <div class="px-3 py-2 border-b border-gray-100 dark:border-white/5 flex justify-between items-center">
                      <span class="text-xs font-medium text-gray-500">历史记录</span>
                      <button @click="clearHistory(); showHistoryDropdown = false" class="text-xs text-red-500 hover:text-red-600">清除</button>
                    </div>
                    <div class="max-h-48 overflow-y-auto">
                      <button 
                        v-for="(cmd, i) in commandHistory" 
                        :key="i"
                        @click="taskSchema = cmd; showHistoryDropdown = false"
                        class="w-full text-left px-3 py-2.5 text-sm text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-white/5 transition-colors border-b border-gray-50 dark:border-white/5 last:border-0"
                        style="word-break: break-all; overflow-wrap: break-word;"
                      >
                        {{ cmd }}
                      </button>
                    </div>
                  </div>
                </div>

                <!-- 任务脚本/列表管理按钮 -->
                <button 
                  @click="openTaskManager"
                  class="w-10 h-10 rounded-xl flex items-center justify-center transition-colors bg-purple-50 text-purple-600 hover:bg-purple-100 dark:bg-purple-500/10 dark:text-purple-400 dark:hover:bg-purple-500/20 mr-1"
                  title="任务列表"
                  v-if="!isRunning"
                >
                  <svg xmlns="http://www.w3.org/2000/svg" class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 6h11"/><path d="M9 12h11"/><path d="M9 18h11"/><path d="M3 6h.01"/><path d="M3 12h.01"/><path d="M3 18h.01"/></svg>
                </button>
                
                <!-- 输入框 -->
                <div class="flex-1 relative">
                  <input 
                    type="text"
                    v-model="taskSchema" 
                    @keydown.enter.exact.prevent="!isRunning && taskSchema && startTask()"
                    @focus="showHistoryDropdown = false"
                    class="w-full h-11 rounded-xl px-4 text-sm focus:outline-none focus:ring-2 transition-all bg-gray-100 dark:bg-[#252525] border-0 text-gray-900 dark:text-white placeholder-gray-400 dark:placeholder-gray-500 focus:ring-[#00C853]/50"
                    :class="isRunning ? 'opacity-50' : ''"
                    placeholder="输入任务..."
                    :disabled="isRunning"
                  />
                </div>
                
                <!-- 发送按钮 -->
                <button 
                  @click="startTask" 
                  :disabled="isRunning || !taskSchema"
                  class="flex-shrink-0 w-11 h-11 rounded-xl bg-[#00C853] hover:bg-[#00E676] disabled:opacity-30 disabled:cursor-not-allowed text-white flex items-center justify-center transition-all active:scale-95"
                >
                  <svg xmlns="http://www.w3.org/2000/svg" class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></svg>
                </button>
              </div>
            </div>
        </div>

    <!-- 设置页面 -->
    <div v-else class="flex flex-col gap-4 flex-1 min-h-0">
            <!-- 头部 -->
            <div class="flex items-center gap-3 pb-2 border-b transition-colors border-gray-200 dark:border-white/5">
                <button @click="showSettings = false" class="p-1 rounded-full hover:bg-black/5 dark:hover:bg-white/10">
                    <svg xmlns="http://www.w3.org/2000/svg" class="w-6 h-6 text-gray-500 dark:text-gray-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m15 18-6-6 6-6"/></svg>
                </button>
                <h2 class="text-lg font-bold text-gray-800 dark:text-white">设置</h2>
            </div>

            <!-- 通用设置 -->
            <div class="rounded-xl p-4 border bg-white border-gray-200 dark:bg-[#1E1E1E] dark:border-white/5">
                <h3 class="text-sm font-bold text-gray-700 dark:text-gray-300 mb-3">通用设置</h3>
                
                <!-- 主题设置 -->
                <div class="flex items-center justify-between py-2">
                    <div>
                        <div class="text-sm text-gray-800 dark:text-white">深色模式</div>
                        <div class="text-xs text-gray-500">切换界面主题</div>
                    </div>
                    <button 
                        @click="toggleTheme"
                        class="w-10 h-5 rounded-full relative transition-colors shadow-inner"
                        :class="isDark ? 'bg-[#00C853]' : 'bg-gray-300 dark:bg-gray-600'"
                    >
                        <span class="absolute top-1 left-1 bg-white w-3 h-3 rounded-full transition-transform shadow-sm" :class="isDark ? 'translate-x-5' : ''"></span>
                    </button>
                </div>
                
                <!-- 日志级别 -->
                <div class="flex items-center justify-between py-2 border-t border-gray-100 dark:border-white/5">
                    <div>
                        <div class="text-sm text-gray-800 dark:text-white">日志级别</div>
                        <div class="text-xs text-gray-500">控制日志详细程度</div>
                    </div>
                    <select 
                        :value="logLevel"
                        @change="saveLogLevel(Number(($event.target as HTMLSelectElement).value))"
                        class="rounded-lg px-3 py-1.5 text-sm bg-gray-50 border border-gray-300 text-gray-900 dark:bg-[#121212] dark:border-white/10 dark:text-white"
                    >
                        <option v-for="opt in logLevelOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
                    </select>
                </div>
                
                
                <!-- 最大步数 -->
                <div class="flex items-center justify-between py-2 border-t border-gray-100 dark:border-white/5">
                    <div>
                        <div class="text-sm text-gray-800 dark:text-white">最大步数</div>
                        <div class="text-xs text-gray-500">单次任务最大执行步数</div>
                    </div>
                    <div class="flex items-center gap-2">
                        <input 
                            type="number" 
                            :value="maxSteps" 
                            @change="saveMaxSteps(Number(($event.target as HTMLInputElement).value))" 
                            class="w-20 rounded-lg px-2 py-1.5 text-sm bg-gray-50 border border-gray-300 text-gray-900 dark:bg-[#121212] dark:border-white/10 dark:text-white text-center"
                            min="10" max="200"
                        >
                    </div>
                </div>

                <!-- 开发者模式 -->
                <div class="flex items-center justify-between py-2 border-t border-gray-100 dark:border-white/5">
                    <div>
                        <div class="text-sm text-gray-800 dark:text-white">开发者模式</div>
                        <div class="text-xs text-gray-500">显示详细日志和 Set-of-Mark 预览</div>
                    </div>
                    <button 
                        @click="toggleDevMode"
                        class="w-10 h-5 rounded-full relative transition-colors shadow-inner"
                        :class="devMode ? 'bg-[#00C853]' : 'bg-gray-300 dark:bg-gray-600'"
                    >
                        <span class="absolute top-1 left-1 bg-white w-3 h-3 rounded-full transition-transform shadow-sm" :class="devMode ? 'translate-x-5' : ''"></span>
                    </button>
                </div>
                
                <!-- 日志查看器入口 (仅开发者模式) -->
                <div v-if="devMode" class="pt-2 border-t border-gray-100 dark:border-white/5 space-y-2">
                    <button 
                        @click="showLogViewer = true"
                        class="w-full text-left px-3 py-2 rounded-lg text-sm bg-gray-50 hover:bg-gray-100 text-gray-700 dark:bg-white/5 dark:hover:bg-white/10 dark:text-gray-300 flex items-center gap-2"
                    >
                        <svg xmlns="http://www.w3.org/2000/svg" class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="4 17 10 11 4 5"/><line x1="12" y1="19" x2="20" y2="19"/></svg>
                        查看完整日志
                    </button>
                    <button 
                        @click="loadSomPreview"
                        :disabled="somPreviewLoading"
                        class="w-full text-left px-3 py-2 rounded-lg text-sm bg-pink-50 hover:bg-pink-100 text-pink-700 dark:bg-pink-500/10 dark:hover:bg-pink-500/20 dark:text-pink-300 flex items-center gap-2"
                    >
                        <svg xmlns="http://www.w3.org/2000/svg" class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"/><circle cx="9" cy="9" r="2"/><path d="m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21"/></svg>
                        <span v-if="somPreviewLoading">正在获取 SoM 预览...</span>
                        <span v-else>查看 Set-of-Marks 预览</span>
                    </button>
                    <button 
                        @click="loadFileLog"
                        :disabled="fileLogLoading"
                        class="w-full text-left px-3 py-2 rounded-lg text-sm bg-blue-50 hover:bg-blue-100 text-blue-700 dark:bg-blue-500/10 dark:hover:bg-blue-500/20 dark:text-blue-300 flex items-center gap-2"
                    >
                        <svg xmlns="http://www.w3.org/2000/svg" class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><polyline points="10 9 9 9 8 9"/></svg>
                        <span v-if="fileLogLoading">正在读取日志文件...</span>
                        <span v-else>查看日志文件</span>
                    </button>
                </div>
            </div>

            <!-- 执行模式与权限 -->
            <div class="rounded-xl p-4 border bg-white border-gray-200 dark:bg-[#1E1E1E] dark:border-white/5">
                <h3 class="text-sm font-bold text-gray-700 dark:text-gray-300 mb-3">执行模式与权限</h3>
                
                <!-- 执行模式选择 -->
                <div class="flex items-center justify-between py-2">
                    <div>
                        <div class="text-sm text-gray-800 dark:text-white">执行模式</div>
                        <div class="text-xs text-gray-500">选择任务执行方式</div>
                    </div>
                    <div class="flex bg-gray-200 dark:bg-gray-700 rounded-lg p-0.5">
                        <button 
                            @click="setMode('accessibility')"
                            class="px-3 py-1 text-xs font-medium rounded-md transition-all"
                            :class="executionMode === 'accessibility' ? 'bg-white dark:bg-gray-600 text-gray-800 dark:text-white shadow-sm' : 'text-gray-500 dark:text-gray-400'"
                        >无障碍</button>
                        <button 
                            @click="setMode('shizuku')"
                            class="px-3 py-1 text-xs font-medium rounded-md transition-all"
                            :class="executionMode === 'shizuku' ? 'bg-white dark:bg-gray-600 text-gray-800 dark:text-white shadow-sm' : 'text-gray-500 dark:text-gray-400'"
                        >Shizuku</button>
                    </div>
                </div>
                
                <!-- 无障碍服务 (仅无障碍模式显示) -->
                <div v-if="executionMode === 'accessibility'" class="flex items-center justify-between py-2 border-t border-gray-100 dark:border-white/5">
                    <div class="flex items-center gap-3">
                        <div class="w-8 h-8 rounded-lg flex items-center justify-center" :class="serviceEnabled ? 'bg-green-100 dark:bg-green-500/20' : 'bg-red-100 dark:bg-red-500/20'">
                            <div class="w-2.5 h-2.5 rounded-full" :class="serviceEnabled ? 'bg-[#00C853]' : 'bg-red-500'"></div>
                        </div>
                        <div>
                            <div class="text-sm text-gray-800 dark:text-white">无障碍服务</div>
                            <div class="text-xs text-gray-500">{{ serviceEnabled ? '已开启' : '未开启 - 用于模拟点击' }}</div>
                        </div>
                    </div>
                    <button 
                        v-if="!serviceEnabled"
                        @click="openAccessibility"
                        class="px-3 py-1.5 text-xs font-medium rounded-lg bg-blue-600 hover:bg-blue-500 text-white"
                    >去开启</button>
                    <span v-else class="text-xs text-[#00C853] font-medium">✓ 已授权</span>
                </div>
                
                <!-- Shizuku 服务 (仅Shizuku模式显示) -->
                <div v-if="executionMode === 'shizuku'" class="flex items-center justify-between py-2 border-t border-gray-100 dark:border-white/5">
                    <div class="flex items-center gap-3">
                        <div class="w-8 h-8 rounded-lg flex items-center justify-center" :class="shizukuStatus.available && shizukuStatus.hasPermission ? 'bg-green-100 dark:bg-green-500/20' : 'bg-red-100 dark:bg-red-500/20'">
                            <div class="w-2.5 h-2.5 rounded-full" :class="shizukuStatus.available && shizukuStatus.hasPermission ? 'bg-[#00C853]' : 'bg-red-500'"></div>
                        </div>
                        <div>
                            <div class="text-sm text-gray-800 dark:text-white">Shizuku 服务</div>
                            <div class="text-xs text-gray-500">{{ shizukuStatus.available ? (shizukuStatus.hasPermission ? '已就绪' : '需要授权') : '未启动 - 请先启动 Shizuku' }}</div>
                        </div>
                    </div>
                    <button 
                        v-if="shizukuStatus.available && !shizukuStatus.hasPermission"
                        @click="requestShizukuPermission"
                        class="px-3 py-1.5 text-xs font-medium rounded-lg bg-purple-600 hover:bg-purple-500 text-white"
                    >授权</button>
                    <span v-else-if="shizukuStatus.available && shizukuStatus.hasPermission" class="text-xs text-[#00C853] font-medium">✓ 已授权</span>
                    <span v-else class="text-xs text-red-400">未启动</span>
                </div>
                
                <!-- 悬浮窗权限 (始终显示) -->
                <div class="flex items-center justify-between py-2 border-t border-gray-100 dark:border-white/5">
                    <div class="flex items-center gap-3">
                        <div class="w-8 h-8 rounded-lg flex items-center justify-center" :class="overlayPermissionValid ? 'bg-green-100 dark:bg-green-500/20' : 'bg-amber-100 dark:bg-amber-500/20'">
                            <div class="w-2.5 h-2.5 rounded-full" :class="overlayPermissionValid ? 'bg-[#00C853]' : 'bg-amber-500'"></div>
                        </div>
                        <div>
                            <div class="text-sm text-gray-800 dark:text-white">悬浮窗权限</div>
                            <div class="text-xs text-gray-500">{{ overlayPermissionValid ? '已开启' : '未开启 - 显示实时状态' }}</div>
                        </div>
                    </div>
                    <button 
                        v-if="!overlayPermissionValid"
                        @click="requestOverlayPermission"
                        class="px-3 py-1.5 text-xs font-medium rounded-lg bg-amber-600 hover:bg-amber-500 text-white"
                    >去开启</button>
                    <span v-else class="text-xs text-[#00C853] font-medium">✓ 已授权</span>
                </div>
            </div>

            <!-- API 模型配置标题 -->
            <div class="flex items-center justify-between">
                <h3 class="text-sm font-bold text-gray-700 dark:text-gray-300">API 模型配置</h3>
                <button @click="openAddDialog" class="bg-[#00C853] text-white dark:text-black px-3 py-1.5 rounded-lg text-sm font-bold flex items-center gap-1 shadow-md hover:bg-[#00E676] transition-colors">
                    <svg xmlns="http://www.w3.org/2000/svg" class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
                    添加
                </button>
            </div>

            <!-- 列表 -->
            <div class="flex-1 overflow-y-auto space-y-3">
                <div v-if="apiConfigs.length === 0" class="text-center py-10 text-gray-500 flex flex-col items-center gap-2">
                    <div class="text-4xl">📭</div>
                    <div>暂无模型配置，请点击右上角添加</div>
                </div>

                <div v-for="config in apiConfigs" :key="config.id" class="rounded-xl p-4 border flex items-center gap-4 transition-colors bg-white border-gray-200 hover:border-gray-300 dark:bg-[#1E1E1E] dark:border-white/5 dark:hover:border-white/10">
                   <div class="flex-1">
                       <div class="flex items-center gap-2 mb-1">
                           <span class="font-bold text-gray-800 dark:text-white">{{ config.name }}</span>
                           <span class="px-2 py-0.5 rounded-full text-[10px] bg-gray-100 text-gray-600 border border-gray-200 dark:bg-white/10 dark:text-gray-300 dark:border-white/5">{{ config.provider }}</span>
                       </div>
                       <div class="text-xs text-gray-500 dark:text-gray-400">{{ config.model }}</div>
                   </div>
                   
                   <div class="flex items-center gap-2">
                        <!-- 开关 -->
                       <button 
                        @click="toggleConfig(config)"
                        class="w-10 h-5 rounded-full relative transition-colors shadow-inner"
                        :class="config.enabled ? 'bg-[#00C853]' : 'bg-gray-300 dark:bg-gray-600'"
                       >
                           <span class="absolute top-1 left-1 bg-white w-3 h-3 rounded-full transition-transform shadow-sm" :class="config.enabled ? 'translate-x-5' : ''"></span>
                       </button>
                       
                       <button @click="editConfig(config)" class="p-2 rounded-lg hover:bg-blue-50 text-gray-400 hover:text-blue-500 dark:hover:bg-blue-500/10 dark:hover:text-blue-400">
                           <svg xmlns="http://www.w3.org/2000/svg" class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17 3a2.828 2.828 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5L17 3z"/></svg>
                       </button>
                       
                       <button @click="deleteConfig(config)" class="p-2 rounded-lg hover:bg-red-50 text-gray-400 hover:text-red-500 dark:hover:bg-red-500/10 dark:hover:text-red-400">
                           <svg xmlns="http://www.w3.org/2000/svg" class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/><line x1="10" y1="11" x2="10" y2="17"/><line x1="14" y1="11" x2="14" y2="17"/></svg>
                       </button>
                   </div>
                </div>
            </div>

            <!-- 编辑对话框 (Modal) -->
            <Transition name="modal">
                <div v-if="showConfigModal" class="fixed inset-0 bg-black/50 dark:bg-black/80 z-50 flex items-center justify-center p-4 backdrop-blur-sm">
                    <div class="rounded-xl w-full max-w-md border shadow-2xl overflow-hidden flex flex-col max-h-[90vh] bg-white border-gray-200 dark:bg-[#1E1E1E] dark:border-white/10">
                        <div class="p-4 border-b flex justify-between items-center bg-gray-50 border-gray-200 dark:bg-[#252525] dark:border-white/5">
                            <h3 class="font-bold text-gray-800 dark:text-white">{{ isEditing ? '编辑模型' : '添加模型' }}</h3>
                            <button @click="showConfigModal = false" class="text-gray-500 hover:text-gray-800 dark:text-gray-400 dark:hover:text-white">✕</button>
                        </div>
                        
                        <div class="p-4 overflow-y-auto space-y-4">
                            <div>
                                <label class="block text-xs font-medium mb-1 text-gray-600 dark:text-gray-400">名称 (可选)</label>
                                <input v-model="form.name" class="w-full rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-[#00C853] bg-white border border-gray-300 text-gray-900 dark:bg-[#121212] dark:border-white/10 dark:text-white" placeholder="例如: 我的智谱AI">
                            </div>
                            
                            <div>
                                <label class="block text-xs font-medium mb-1 text-gray-600 dark:text-gray-400">服务商</label>
                                <select v-model="form.provider" @change="onProviderChange" class="w-full rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-[#00C853] bg-white border border-gray-300 text-gray-900 dark:bg-[#121212] dark:border-white/10 dark:text-white">
                                    <option v-for="p in providers" :key="p.value" :value="p.value">{{ p.label }}</option>
                                </select>
                            </div>

                            <div>
                                <label class="block text-xs font-medium mb-1 text-gray-600 dark:text-gray-400">模型</label>
                                <div v-if="currentModels.length > 0" class="mb-2">
                                     <select v-model="form.selectedModel" @change="onModelSelectChange" class="w-full rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-[#00C853] bg-white border border-gray-300 text-gray-900 dark:bg-[#121212] dark:border-white/10 dark:text-white mb-2">
                                        <option v-for="m in currentModels" :key="m.id" :value="m.id">{{ m.name }}</option>
                                        <option value="custom">自定义模型...</option>
                                    </select>
                                </div>
                                <input v-if="form.selectedModel === 'custom' || currentModels.length === 0" v-model="form.customModel" class="w-full rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-[#00C853] bg-white border border-gray-300 text-gray-900 dark:bg-[#121212] dark:border-white/10 dark:text-white" placeholder="输入模型 ID">
                            </div>

                            <div>
                                <label class="block text-xs font-medium mb-1 text-gray-600 dark:text-gray-400">API Key</label>
                                <input type="password" v-model="form.apiKey" class="w-full rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-[#00C853] bg-white border border-gray-300 text-gray-900 dark:bg-[#121212] dark:border-white/10 dark:text-white" placeholder="sk-...">
                            </div>

                            <div v-if="form.provider === 'OPENAI_COMPATIBLE'">
                                <label class="block text-xs font-medium mb-1 text-gray-600 dark:text-gray-400">API 端点 URL</label>
                                <input v-model="form.customEndpoint" class="w-full rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-[#00C853] bg-white border border-gray-300 text-gray-900 dark:bg-[#121212] dark:border-white/10 dark:text-white" placeholder="https://api.example.com/v1/chat/completions">
                            </div>

                        </div>
                        
                        <div class="p-4 border-t flex gap-3 bg-gray-50 border-gray-200 dark:bg-[#252525] dark:border-white/5">
                            <button @click="showConfigModal = false" class="flex-1 py-2 rounded-lg bg-gray-200 hover:bg-gray-300 text-gray-800 dark:bg-gray-700 dark:hover:bg-gray-600 dark:text-white text-sm font-medium transition-colors">取消</button>
                            <button @click="saveForm" class="flex-1 py-2 rounded-lg bg-[#00C853] hover:bg-[#00E676] text-white dark:text-black text-sm font-bold transition-colors">保存</button>
                        </div>
                    </div>
                </div>
            </Transition>
        </div>

    <!-- Log Viewer Modal (开发者模式) -->
    <Transition name="modal">
      <div v-if="showLogViewer" class="fixed inset-0 bg-black/50 dark:bg-black/80 z-50 flex items-center justify-center p-4 backdrop-blur-sm">
        <div class="rounded-xl w-full max-w-2xl h-[80vh] border shadow-2xl overflow-hidden flex flex-col bg-white border-gray-200 dark:bg-[#1E1E1E] dark:border-white/10">
          <div class="p-4 border-b flex justify-between items-center bg-gray-50 border-gray-200 dark:bg-[#252525] dark:border-white/5">
            <div class="flex items-center gap-2">
              <svg xmlns="http://www.w3.org/2000/svg" class="w-5 h-5 text-gray-500" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="4 17 10 11 4 5"/><line x1="12" y1="19" x2="20" y2="19"/></svg>
              <h3 class="font-bold text-gray-800 dark:text-white">完整日志</h3>
            </div>
            <div class="flex items-center gap-2">
              <button @click="clearLogs" class="px-2 py-1 text-xs rounded bg-gray-200 hover:bg-gray-300 text-gray-700 dark:bg-gray-700 dark:hover:bg-gray-600 dark:text-gray-300">清空</button>
              <button @click="showLogViewer = false" class="text-gray-500 hover:text-gray-800 dark:text-gray-400 dark:hover:text-white">✕</button>
            </div>
          </div>
          
          <div class="flex-1 overflow-y-auto p-4 font-mono text-xs space-y-1 bg-[#fafafa] dark:bg-[#121212]">
            <div v-if="logs.length === 0" class="text-center text-gray-400 py-10">暂无日志</div>
            <div v-for="(log, index) in logs" :key="index" class="flex gap-2 py-0.5 border-l-2 pl-2" :class="getLogBorderColor(log.type)">
              <span class="text-gray-400 dark:text-gray-500 text-[10px] w-[50px] flex-shrink-0">{{ formatTime(log.timestamp) }}</span>
              <span class="px-1 py-0.5 rounded text-[10px] font-medium" :class="getLogBadgeClass(log.type)">{{ log.type }}</span>
              <span :class="getLogColor(log.type)" class="flex-1 whitespace-pre-wrap break-all">{{ log.message }}</span>
            </div>
          </div>
        </div>
      </div>
    </Transition>

    <!-- Conversation History Sidebar -->
    <Transition name="slide-in">
      <div v-if="showChatHistory" class="fixed inset-0 z-50 flex">
        <div class="absolute inset-0 bg-black/30" @click="showChatHistory = false"></div>
        <div class="relative w-72 h-full bg-white dark:bg-[#1E1E1E] shadow-xl flex flex-col">
          <div class="p-4 border-b border-gray-200 dark:border-white/5 flex items-center justify-between">
            <h3 class="font-bold text-gray-800 dark:text-white">对话历史</h3>
            <button @click="showChatHistory = false" class="text-gray-400 hover:text-gray-600 dark:hover:text-white">✕</button>
          </div>
          
          <div class="flex-1 overflow-y-auto">
            <div v-if="chatSessions.length === 0" class="text-center text-gray-400 py-10 text-sm">暂无对话历史</div>
            <div 
              v-for="session in chatSessions" 
              :key="session.id"
              @click="loadChatSession(session)"
              class="p-3 border-b border-gray-100 dark:border-white/5 hover:bg-gray-50 dark:hover:bg-white/5 cursor-pointer group"
            >
              <div class="flex items-start justify-between">
                <div class="flex-1 min-w-0 pr-2">
                   <!-- 正常显示模式 -->
                   <div v-if="editingSessionId !== session.id">
                      <div class="text-sm font-medium text-gray-800 dark:text-white truncate" :title="session.title">{{ session.title }}</div>
                      <div class="text-xs text-gray-400 mt-0.5">{{ formatDate(session.createdAt) }}</div>
                   </div>
                   <!-- 编辑模式 -->
                   <div v-else class="flex items-center gap-1" @click.stop>
                      <input 
                        ref="renameInput"
                        v-model="renameTitle"
                        @keydown.enter="saveRenameSession(session)"
                        @keydown.esc="cancelRenameSession"
                        class="w-full text-sm px-1 py-0.5 rounded border border-[#00C853] bg-white dark:bg-[#121212] focus:outline-none"
                      />
                      <button @click="saveRenameSession(session)" class="text-green-500 hover:text-green-600"><svg xmlns="http://www.w3.org/2000/svg" class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg></button>
                      <button @click="cancelRenameSession" class="text-gray-400 hover:text-gray-600"><svg xmlns="http://www.w3.org/2000/svg" class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg></button>
                   </div>
                </div>
                
                <!-- 列表操作栏 -->
                <div class="flex flex-col gap-1 opacity-0 group-hover:opacity-100 transition-opacity" v-if="editingSessionId !== session.id">
                   <button 
                    @click.stop="startRenameSession(session)"
                    class="p-1 text-gray-400 hover:text-blue-500 transition-colors"
                    title="重命名"
                   >
                     <svg xmlns="http://www.w3.org/2000/svg" class="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
                   </button>
                   <button 
                    @click.stop="deleteChatSession(session.id)"
                    class="p-1 text-gray-400 hover:text-red-500 transition-colors"
                    title="删除"
                   >
                     <svg xmlns="http://www.w3.org/2000/svg" class="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
                   </button>
                </div>
              </div>
            </div>
          </div>
          
          <div class="p-3 border-t border-gray-200 dark:border-white/5">
            <button 
              @click="startNewChat(); showChatHistory = false"
              class="w-full py-2 rounded-lg bg-[#00C853] hover:bg-[#00E676] text-white dark:text-black font-medium text-sm flex items-center justify-center gap-2"
            >
              <svg xmlns="http://www.w3.org/2000/svg" class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
              新对话
            </button>
          </div>
        </div>
      </div>
    </Transition>

    <!-- SoM Preview Modal -->
    <Transition name="modal">
      <div v-if="showSomPreview" class="fixed inset-0 bg-black/80 z-[100] flex items-center justify-center p-4 backdrop-blur-sm" @click.self="showSomPreview = false">
        <div class="relative w-full max-w-lg bg-white dark:bg-[#252525] rounded-2xl shadow-xl overflow-hidden">
          <div class="px-4 py-3 border-b border-gray-100 dark:border-white/10 flex items-center justify-between">
            <h3 class="text-base font-bold text-gray-800 dark:text-white flex items-center gap-2">
              <span class="w-6 h-6 rounded-full bg-pink-500 text-white text-xs flex items-center justify-center font-bold">1</span>
              Set-of-Marks 预览
            </h3>
            <button @click="showSomPreview = false" class="w-8 h-8 rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 flex items-center justify-center text-gray-500 dark:text-gray-400">
              <svg xmlns="http://www.w3.org/2000/svg" class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
            </button>
          </div>
          <div class="p-2 max-h-[70vh] overflow-auto">
            <img v-if="somPreviewImage" :src="somPreviewImage" alt="SoM Preview" class="w-full h-auto rounded-lg" />
            <div v-else class="text-center text-gray-500 py-8">暂无预览图</div>
          </div>
          <div class="px-4 py-3 bg-gray-50 dark:bg-[#1a1a1a] text-xs text-gray-500 dark:text-gray-400">
            粉色圆圈标记了当前屏幕上的可点击元素，AI 可以通过数字 ID 直接引用这些元素进行操作。
          </div>
        </div>
      </div>
    </Transition>

    <!-- File Log Viewer Modal -->
    <Transition name="modal">
      <div v-if="showFileLog" class="fixed inset-0 bg-black/50 dark:bg-black/70 z-[100] flex items-center justify-center p-4 backdrop-blur-sm" @click.self="showFileLog = false">
        <div class="w-full max-w-2xl max-h-[85vh] bg-white dark:bg-[#252525] rounded-2xl shadow-xl overflow-hidden flex flex-col">
          <div class="p-4 border-b border-gray-100 dark:border-white/10 flex items-center justify-between">
            <h3 class="text-lg font-semibold text-gray-800 dark:text-gray-200">📄 日志文件查看器</h3>
            <button @click="showFileLog = false" class="p-2 rounded-lg hover:bg-gray-100 dark:hover:bg-white/10 transition-colors">
              <svg xmlns="http://www.w3.org/2000/svg" class="w-5 h-5 text-gray-500" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
            </button>
          </div>
          <div class="flex-1 overflow-auto p-4 bg-gray-50 dark:bg-[#1a1a1a]">
            <pre class="text-xs font-mono text-gray-700 dark:text-gray-300 whitespace-pre-wrap break-words leading-relaxed">{{ fileLogContent }}</pre>
          </div>
          <div class="px-4 py-3 bg-gray-50 dark:bg-[#1a1a1a] text-xs text-gray-500 dark:text-gray-400 border-t border-gray-100 dark:border-white/10 flex justify-between items-center">
            <span>最后 300 行日志</span>
            <button @click="loadFileLog" class="px-3 py-1 bg-blue-500 hover:bg-blue-600 text-white rounded text-xs">刷新</button>
          </div>
        </div>
      </div>
    </Transition>

    <!-- Custom Confirm Modal -->
    <Transition name="modal">
      <div v-if="showConfirmModal" class="fixed inset-0 bg-black/50 dark:bg-black/70 z-[100] flex items-center justify-center p-4 backdrop-blur-sm">
        <div class="w-full max-w-xs bg-white dark:bg-[#252525] rounded-2xl shadow-xl overflow-hidden">
          <div class="p-5 text-center">
            <div class="w-12 h-12 mx-auto mb-4 rounded-full bg-amber-100 dark:bg-amber-500/20 flex items-center justify-center">
              <svg xmlns="http://www.w3.org/2000/svg" class="w-6 h-6 text-amber-600 dark:text-amber-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
            </div>
            <p class="text-sm text-gray-700 dark:text-gray-300">{{ confirmMessage }}</p>
          </div>
          <div class="flex border-t border-gray-100 dark:border-white/10">
            <button 
              @click="handleCancel"
              class="flex-1 py-3 text-sm font-medium text-gray-600 dark:text-gray-400 hover:bg-gray-50 dark:hover:bg-white/5 transition-colors"
            >取消</button>
            <button 
              @click="handleConfirm"
              class="flex-1 py-3 text-sm font-medium text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-500/10 transition-colors border-l border-gray-100 dark:border-white/10"
            >确定</button>
          </div>
        </div>
      </div>
    </Transition>

    <!-- Task Manager Modal -->
    <Transition name="modal">
        <div v-if="showTaskManager" class="fixed inset-0 bg-black/50 dark:bg-black/80 z-50 flex items-center justify-center p-4 backdrop-blur-sm">
            <div class="rounded-xl w-full max-w-md border shadow-2xl overflow-hidden flex flex-col max-h-[85vh] bg-white border-gray-200 dark:bg-[#1E1E1E] dark:border-white/10">
                <div class="p-4 border-b flex justify-between items-center bg-gray-50 border-gray-200 dark:bg-[#252525] dark:border-white/5">
                    <h3 class="font-bold text-gray-800 dark:text-white flex items-center gap-2">
                        <svg xmlns="http://www.w3.org/2000/svg" class="w-5 h-5 text-purple-500" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 6h11"/><path d="M9 12h11"/><path d="M9 18h11"/><path d="M3 6h.01"/><path d="M3 12h.01"/><path d="M3 18h.01"/></svg>
                        任务列表管理
                    </h3>
                    <button @click="showTaskManager = false" class="text-gray-500 hover:text-gray-800 dark:text-gray-400 dark:hover:text-white">✕</button>
                </div>
                
                <div class="p-2 border-b border-gray-100 dark:border-white/5 flex gap-1.5 flex-wrap">
                    <button @click="createNewTaskList" class="flex items-center gap-1 px-2 py-1 bg-[#00C853] text-white rounded-md text-xs font-medium hover:bg-[#00E676]">
                        <svg xmlns="http://www.w3.org/2000/svg" class="w-3 h-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
                        新建
                    </button>
                    <button @click="openImportModal" class="flex items-center gap-1 px-2 py-1 bg-blue-500/10 text-blue-500 dark:text-blue-300 rounded-md text-xs font-medium hover:bg-blue-500/20">
                        <svg xmlns="http://www.w3.org/2000/svg" class="w-3 h-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="14 9 9 4 4 9"/><path d="M20 20h-7a4 4 0 0 1-4-4V4"/></svg>
                        对话
                    </button>
                    <button @click="importFromFile" class="flex items-center gap-1 px-2 py-1 bg-purple-500/10 text-purple-500 dark:text-purple-300 rounded-md text-xs font-medium hover:bg-purple-500/20">
                        <svg xmlns="http://www.w3.org/2000/svg" class="w-3 h-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
                        文件
                    </button>
                </div>
                
                <div class="flex-1 overflow-y-auto p-2 space-y-1.5">
                    <div v-if="customTaskLists.length === 0" class="text-center py-8 text-gray-400 text-sm">
                        暂无任务列表
                    </div>
                    <div v-for="list in customTaskLists" :key="list.id" class="border rounded-lg p-2 bg-gray-50 dark:bg-white/5 border-gray-200 dark:border-white/5 group hover:border-purple-300 dark:hover:border-purple-500/50 transition-colors">
                        <div class="flex justify-between items-center">
                            <div class="flex-1 min-w-0">
                                <div class="font-medium text-sm text-gray-800 dark:text-gray-200 truncate">{{ list.name }}</div>
                                <div class="text-[10px] text-gray-400 flex items-center gap-1">
                                    <span>{{ getScriptLineCount(list.script) }}条</span>
                                    <span>·</span>
                                    <span>{{ formatDate(list.createdAt) }}</span>
                                </div>
                            </div>
                            <div class="flex items-center gap-1 ml-2">
                                <button @click="runTaskList(list)" class="px-2 py-0.5 bg-purple-600 text-white rounded text-[10px] font-bold hover:bg-purple-500 flex items-center gap-0.5">
                                    <svg xmlns="http://www.w3.org/2000/svg" class="w-2.5 h-2.5" viewBox="0 0 24 24" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3"/></svg>
                                    运行
                                </button>
                                <button @click="exportTaskList(list)" class="p-1 text-gray-400 hover:text-purple-500 hover:bg-purple-50 dark:hover:bg-purple-900/20 rounded" title="导出">
                                    <svg xmlns="http://www.w3.org/2000/svg" class="w-3 h-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
                                </button>
                                <button @click="editTaskList(list)" class="p-1 text-gray-400 hover:text-blue-500 hover:bg-blue-50 dark:hover:bg-blue-900/20 rounded" title="编辑">
                                    <svg xmlns="http://www.w3.org/2000/svg" class="w-3 h-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
                                </button>
                                <button @click="deleteTaskList(list.id)" class="p-1 text-gray-400 hover:text-red-500 hover:bg-red-50 dark:hover:bg-red-900/20 rounded" title="删除">
                                    <svg xmlns="http://www.w3.org/2000/svg" class="w-3 h-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </Transition>

    <!-- Task Editor Modal -->
    <Transition name="modal">
        <div v-if="showTaskEditor" class="fixed inset-0 bg-black/50 dark:bg-black/80 z-[60] flex items-center justify-center p-4 backdrop-blur-sm">
            <div class="rounded-xl w-full max-w-lg border shadow-2xl overflow-hidden flex flex-col h-[80vh] bg-white border-gray-200 dark:bg-[#1E1E1E] dark:border-white/10">
                <div class="p-4 border-b flex justify-between items-center bg-gray-50 border-gray-200 dark:bg-[#252525] dark:border-white/5">
                    <h3 class="font-bold text-gray-800 dark:text-white">编辑任务列表</h3>
                    <button @click="showTaskEditor = false" class="text-gray-500 hover:text-gray-800 dark:text-gray-400 dark:hover:text-white">✕</button>
                </div>
                
                <div class="flex-1 overflow-y-auto p-4 space-y-4">
                    <div>
                        <label class="block text-xs font-medium mb-1 text-gray-600 dark:text-gray-400">列表名称</label>
                        <input v-model="editingList.name" class="w-full rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-[#00C853] bg-white border border-gray-300 text-gray-900 dark:bg-[#121212] dark:border-white/10 dark:text-white" placeholder="任务列表名称">
                    </div>
                    
                    <div class="flex-1 flex flex-col min-h-0">
                        <div class="flex justify-between items-center mb-2">
                            <label class="block text-xs font-medium text-gray-600 dark:text-gray-400">脚本指令 ({{ editingCommands.length }} 条)</label>
                            <button 
                                @click="addScriptCommand"
                                class="flex items-center gap-1 px-2 py-1 bg-[#00C853] text-white rounded text-xs font-medium hover:bg-[#00E676]"
                            >
                                <svg xmlns="http://www.w3.org/2000/svg" class="w-3 h-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
                                添加
                            </button>
                        </div>
                        
                        <div class="flex-1 overflow-y-auto space-y-1 max-h-[50vh]">
                            <div v-if="editingCommands.length === 0" class="text-center py-8 text-gray-400 text-sm">
                                暂无指令，点击"添加"开始
                            </div>
                            
                            <!-- Scratch-style block rendering -->
                            <template v-for="(cmd, idx) in editingCommands" :key="idx">
                                <!-- Loop/Loop_time block start (C-shaped wrapper) -->
                                <div v-if="cmd.type === 'loop' || cmd.type === 'loop_time'"
                                    class="relative"
                                    :class="getLoopBlockClass(cmd.type)"
                                >
                                    <!-- Loop header -->
                                    <div class="flex items-center gap-2 p-2 rounded-t-lg cursor-pointer select-none"
                                        :class="getLoopHeaderClass(cmd.type)"
                                        @click="toggleBlockCollapsed(idx)"
                                    >
                                        <svg xmlns="http://www.w3.org/2000/svg" class="w-4 h-4 transition-transform" :class="{'rotate-90': !collapsedBlocks[idx]}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
                                        <span class="text-white font-bold text-sm">{{ cmd.type === 'loop' ? '🔄 循环' : '⏱️ 定时循环' }}</span>
                                        <input 
                                            v-model="editingCommands[idx].param1"
                                            @click.stop
                                            class="w-16 px-2 py-0.5 rounded text-xs font-mono bg-white/20 border border-white/30 text-white placeholder-white/50 focus:outline-none focus:bg-white/30"
                                            :placeholder="cmd.type === 'loop' ? '次数' : '毫秒'"
                                        />
                                        <span class="text-white/80 text-xs">{{ cmd.type === 'loop' ? '次' : 'ms' }}</span>
                                        <div class="flex-1"></div>
                                        <button @click.stop="removeCommand(idx)" class="p-1 text-white/60 hover:text-white">
                                            <svg xmlns="http://www.w3.org/2000/svg" class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
                                        </button>
                                    </div>
                                    <!-- Nested content area (collapsible) -->
                                    <div v-show="!collapsedBlocks[idx]" class="pl-4 py-1 border-l-4"
                                        :class="getLoopContentBorderClass(cmd.type)"
                                    >
                                        <div class="text-xs text-gray-400 dark:text-gray-500 py-1 italic">
                                            (以下命令将循环执行)
                                        </div>
                                    </div>
                                </div>
                                
                                <!-- Repeat Start block -->
                                <div v-else-if="cmd.type === 'repeat_start'"
                                    class="border-l-4 border-purple-500 bg-purple-600 rounded-lg p-2 flex items-center gap-2"
                                >
                                    <span class="text-white font-bold text-sm">🔁 重复开始</span>
                                    <div class="flex-1"></div>
                                    <button @click="removeCommand(idx)" class="p-1 text-white/60 hover:text-white">
                                        <svg xmlns="http://www.w3.org/2000/svg" class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
                                    </button>
                                </div>
                                
                                <!-- Repeat End block -->
                                <div v-else-if="cmd.type === 'repeat_end'"
                                    class="border-l-4 border-purple-500 bg-purple-500 rounded-lg p-2 flex items-center gap-2"
                                >
                                    <span class="text-white font-bold text-sm">🔁 重复结束</span>
                                    <input 
                                        v-model="editingCommands[idx].param1"
                                        class="w-16 px-2 py-0.5 rounded text-xs font-mono bg-white/20 border border-white/30 text-white placeholder-white/50 focus:outline-none focus:bg-white/30"
                                        placeholder="次数"
                                    />
                                    <span class="text-white/70 text-xs">次</span>
                                    <div class="flex-1"></div>
                                    <button @click="removeCommand(idx)" class="p-1 text-white/60 hover:text-white">
                                        <svg xmlns="http://www.w3.org/2000/svg" class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
                                    </button>
                                </div>
                                
                                <!-- Repeat Next Command -->
                                <div v-else-if="cmd.type === 'repeat'"
                                    class="flex items-center gap-2 p-2 rounded-lg bg-indigo-600"
                                >
                                    <span class="text-white font-bold text-sm">🔁 重复下条</span>
                                    <input 
                                        v-model="editingCommands[idx].param1"
                                        class="w-16 px-2 py-0.5 rounded text-xs font-mono bg-white/20 border border-white/30 text-white placeholder-white/50 focus:outline-none focus:bg-white/30"
                                        placeholder="次数"
                                    />
                                    <span class="text-white/80 text-xs">次</span>
                                    <div class="flex-1"></div>
                                    <button @click="removeCommand(idx)" class="p-1 text-white/60 hover:text-white">
                                        <svg xmlns="http://www.w3.org/2000/svg" class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
                                    </button>
                                </div>
                                
                                <!-- Regular command card -->
                                <div v-else
                                    class="p-2 rounded-lg border bg-gray-50 border-gray-200 dark:bg-white/5 dark:border-white/10 group"
                                    :class="getCommandNestingClass(idx)"
                                >
                                    <div class="flex items-center gap-2 mb-2">
                                        <!-- Reorder buttons -->
                                        <div class="flex flex-col gap-0.5">
                                            <button 
                                                @click="moveCommandUp(idx)" 
                                                :disabled="idx === 0"
                                                class="p-0.5 text-gray-400 hover:text-gray-600 disabled:opacity-30 disabled:cursor-not-allowed"
                                            >
                                                <svg xmlns="http://www.w3.org/2000/svg" class="w-3 h-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="18 15 12 9 6 15"/></svg>
                                            </button>
                                            <button 
                                                @click="moveCommandDown(idx)" 
                                                :disabled="idx === editingCommands.length - 1"
                                                class="p-0.5 text-gray-400 hover:text-gray-600 disabled:opacity-30 disabled:cursor-not-allowed"
                                            >
                                                <svg xmlns="http://www.w3.org/2000/svg" class="w-3 h-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="6 9 12 15 18 9"/></svg>
                                            </button>
                                        </div>
                                        
                                        <span class="text-xs text-gray-400 w-5">{{ idx + 1 }}</span>
                                        
                                        <!-- Command type dropdown -->
                                        <select 
                                            v-model="editingCommands[idx].type"
                                            class="px-2 py-1.5 rounded border text-sm bg-white border-gray-300 text-gray-800 dark:bg-[#121212] dark:border-white/10 dark:text-gray-300 focus:outline-none focus:border-[#00C853]"
                                        >
                                            <option v-for="ct in commandTypes" :key="ct.id" :value="ct.id">{{ ct.label }}</option>
                                        </select>
                                        
                                        <!-- Coordinate picker button (for tap, swipe, etc) -->
                                        <button 
                                            v-if="needsCoordinates(cmd.type)"
                                            @click="openCoordPicker(idx)"
                                            class="px-2 py-1 bg-blue-500 text-white rounded text-xs hover:bg-blue-600 flex items-center gap-1"
                                            title="选取坐标"
                                        >
                                            <svg xmlns="http://www.w3.org/2000/svg" class="w-3 h-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="16"/><line x1="8" y1="12" x2="16" y2="12"/></svg>
                                            选取
                                        </button>
                                        
                                        <div class="flex-1"></div>
                                        
                                        <!-- Delete button -->
                                        <button 
                                            @click="removeCommand(idx)"
                                            class="p-1 text-red-400 hover:text-red-600 opacity-50 group-hover:opacity-100 transition-opacity"
                                        >
                                            <svg xmlns="http://www.w3.org/2000/svg" class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
                                        </button>
                                    </div>
                                    
                                    <!-- Dynamic parameter inputs -->
                                    <div v-if="getCommandParams(cmd.type).length > 0" class="flex flex-wrap gap-2 ml-10">
                                        <div v-if="getCommandParams(cmd.type).length >= 1" class="flex items-center gap-1">
                                            <span class="text-xs text-gray-400">{{ getCommandParams(cmd.type)[0] }}:</span>
                                            <input 
                                                v-model="editingCommands[idx].param1"
                                                class="w-20 px-2 py-1 rounded border text-xs font-mono bg-white border-gray-300 text-gray-800 dark:bg-[#121212] dark:border-white/10 dark:text-gray-300 focus:outline-none focus:border-[#00C853]"
                                                :placeholder="getCommandParams(cmd.type)[0]"
                                            />
                                        </div>
                                        <div v-if="getCommandParams(cmd.type).length >= 2" class="flex items-center gap-1">
                                            <span class="text-xs text-gray-400">{{ getCommandParams(cmd.type)[1] }}:</span>
                                            <input 
                                                v-model="editingCommands[idx].param2"
                                                class="w-20 px-2 py-1 rounded border text-xs font-mono bg-white border-gray-300 text-gray-800 dark:bg-[#121212] dark:border-white/10 dark:text-gray-300 focus:outline-none focus:border-[#00C853]"
                                                :placeholder="getCommandParams(cmd.type)[1]"
                                            />
                                        </div>
                                        <div v-if="getCommandParams(cmd.type).length >= 3" class="flex items-center gap-1">
                                            <span class="text-xs text-gray-400">{{ getCommandParams(cmd.type)[2] }}:</span>
                                            <input 
                                                v-model="editingCommands[idx].param3"
                                                class="w-20 px-2 py-1 rounded border text-xs font-mono bg-white border-gray-300 text-gray-800 dark:bg-[#121212] dark:border-white/10 dark:text-gray-300 focus:outline-none focus:border-[#00C853]"
                                                :placeholder="getCommandParams(cmd.type)[2]"
                                            />
                                        </div>
                                        <div v-if="getCommandParams(cmd.type).length >= 4" class="flex items-center gap-1">
                                            <span class="text-xs text-gray-400">{{ getCommandParams(cmd.type)[3] }}:</span>
                                            <input 
                                                v-model="editingCommands[idx].param4"
                                                class="w-20 px-2 py-1 rounded border text-xs font-mono bg-white border-gray-300 text-gray-800 dark:bg-[#121212] dark:border-white/10 dark:text-gray-300 focus:outline-none focus:border-[#00C853]"
                                                :placeholder="getCommandParams(cmd.type)[3]"
                                            />
                                        </div>
                                    </div>
                                </div>
                            </template>
                        </div>
                    </div>
                </div>
                
                <div class="p-4 border-t flex gap-3 bg-gray-50 border-gray-200 dark:bg-[#252525] dark:border-white/5">
                    <button @click="showTaskEditor = false" class="flex-1 py-2 rounded-lg bg-gray-200 hover:bg-gray-300 text-gray-800 dark:bg-gray-700 dark:hover:bg-gray-600 dark:text-white text-sm font-medium transition-colors">取消</button>
                    <button @click="saveTaskList" class="flex-1 py-2 rounded-lg bg-[#00C853] hover:bg-[#00E676] text-white dark:text-black text-sm font-bold transition-colors">保存</button>
                </div>
            </div>
        </div>
    </Transition>

    <!-- Import Modal -->
    <Transition name="modal">
        <div v-if="showImportModal" class="fixed inset-0 bg-black/50 dark:bg-black/80 z-[70] flex items-center justify-center p-4 backdrop-blur-sm">
            <div class="rounded-xl w-full max-w-md border shadow-2xl overflow-hidden flex flex-col max-h-[85vh] bg-white border-gray-200 dark:bg-[#1E1E1E] dark:border-white/10">
                <div class="p-4 border-b flex justify-between items-center bg-gray-50 border-gray-200 dark:bg-[#252525] dark:border-white/5">
                    <h3 class="font-bold text-gray-800 dark:text-white">从对话导入指令</h3>
                    <button @click="showImportModal = false" class="text-gray-500 hover:text-gray-800 dark:text-gray-400 dark:hover:text-white">✕</button>
                </div>
                
                <div class="p-3 border-b border-gray-100 dark:border-white/5">
                    <label class="block text-xs font-medium mb-1 text-gray-600 dark:text-gray-400">选择对话会话</label>
                    <select v-model="importSelection.sessionId" class="w-full rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-[#00C853] bg-white border border-gray-300 text-gray-900 dark:bg-[#121212] dark:border-white/10 dark:text-white">
                        <option v-for="s in chatSessions" :key="s.id" :value="s.id">{{ s.title }} ({{ formatDate(s.createdAt) }})</option>
                    </select>
                </div>
                
                <div class="flex-1 overflow-y-auto p-2 space-y-1">
                    <div v-if="getSessionMessages.length === 0" class="text-center py-8 text-gray-400 text-sm">该会话无可导入的脚本命令</div>
                    <div 
                        v-for="(msg, idx) in getSessionMessages" 
                        :key="idx"
                        @click="toggleImportSelection(idx)"
                        class="p-2 rounded-lg border cursor-pointer transition-colors text-sm flex items-start gap-2"
                        :class="importSelection.selectedIndices.has(idx) ? 'bg-blue-50 border-blue-200 dark:bg-blue-900/20 dark:border-blue-800' : 'bg-white border-gray-100 dark:bg-white/5 dark:border-white/5 hover:bg-gray-50 dark:hover:bg-white/10'"
                    >
                        <div class="w-4 h-4 rounded border flex items-center justify-center mt-0.5 bg-white dark:bg-transparent" :class="importSelection.selectedIndices.has(idx) ? 'border-blue-500 bg-blue-500' : 'border-gray-300 dark:border-gray-600'">
                            <svg v-if="importSelection.selectedIndices.has(idx)" xmlns="http://www.w3.org/2000/svg" class="w-3 h-3 text-white" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>
                        </div>
                        <code class="flex-1 text-green-600 dark:text-green-400 break-all font-mono text-xs">{{ msg.content }}</code>
                    </div>
                </div>
                
                <div class="p-4 border-t flex gap-3 bg-gray-50 border-gray-200 dark:bg-[#252525] dark:border-white/5">
                    <button @click="showImportModal = false" class="flex-1 py-2 rounded-lg bg-gray-200 hover:bg-gray-300 text-gray-800 dark:bg-gray-700 dark:hover:bg-gray-600 dark:text-white text-sm font-medium transition-colors">取消</button>
                    <button @click="importSelectedTasks" class="flex-1 py-2 rounded-lg bg-blue-600 hover:bg-blue-700 text-white text-sm font-bold transition-colors">
                        导入选中 ({{ importSelection.selectedIndices.size }})
                    </button>
                </div>
            </div>
        </div>
    </Transition>

    <!-- Coordinate Picker Modal -->
    <Transition name="modal">
        <div v-if="showCoordPicker" class="fixed inset-0 bg-black/70 z-[80] flex items-center justify-center p-4 backdrop-blur-sm">
            <div class="rounded-xl w-full max-w-sm border shadow-2xl overflow-hidden flex flex-col bg-white border-gray-200 dark:bg-[#1E1E1E] dark:border-white/10">
                <div class="p-4 border-b flex justify-between items-center bg-gray-50 border-gray-200 dark:bg-[#252525] dark:border-white/5">
                    <h3 class="font-bold text-gray-800 dark:text-white">选取坐标</h3>
                    <button @click="showCoordPicker = false" class="text-gray-500 hover:text-gray-800 dark:text-gray-400 dark:hover:text-white">✕</button>
                </div>
                
                <div class="p-4">
                    <!-- Visual phone screen (1000x1000 normalized) -->
                    <div 
                        class="relative w-full aspect-[9/16] bg-gradient-to-b from-gray-800 to-gray-900 rounded-lg overflow-hidden border-4 border-gray-700"
                        @click="handlePickerClick"
                        ref="pickerArea"
                    >
                        <!-- Grid lines -->
                        <div class="absolute inset-0 opacity-20">
                            <div v-for="i in 9" :key="'v'+i" class="absolute bg-white/30" :style="{left: (i*10)+'%', top: 0, width: '1px', height: '100%'}"></div>
                            <div v-for="i in 9" :key="'h'+i" class="absolute bg-white/30" :style="{top: (i*10)+'%', left: 0, height: '1px', width: '100%'}"></div>
                        </div>
                        
                        <!-- Start marker (for single tap or swipe start) -->
                        <div 
                            class="absolute w-8 h-8 bg-green-500 rounded-full border-2 border-white shadow-lg cursor-move flex items-center justify-center transform -translate-x-1/2 -translate-y-1/2 z-10"
                            :style="{left: (pickerX1/10) + '%', top: (pickerY1/10) + '%'}"
                            @mousedown="startDrag($event, 'start')"
                            @touchstart.prevent="startDrag($event, 'start')"
                        >
                            <span class="text-white text-xs font-bold">{{ coordPickerMode === 'swipe' ? '起' : '●' }}</span>
                        </div>
                        
                        <!-- End marker (for swipe only) -->
                        <div 
                            v-if="coordPickerMode === 'swipe'"
                            class="absolute w-8 h-8 bg-red-500 rounded-full border-2 border-white shadow-lg cursor-move flex items-center justify-center transform -translate-x-1/2 -translate-y-1/2 z-10"
                            :style="{left: (pickerX2/10) + '%', top: (pickerY2/10) + '%'}"
                            @mousedown="startDrag($event, 'end')"
                            @touchstart.prevent="startDrag($event, 'end')"
                        >
                            <span class="text-white text-xs font-bold">终</span>
                        </div>
                        
                        <!-- Line between markers for swipe -->
                        <svg v-if="coordPickerMode === 'swipe'" class="absolute inset-0 w-full h-full pointer-events-none">
                            <line 
                                :x1="(pickerX1/10) + '%'" 
                                :y1="(pickerY1/10) + '%'" 
                                :x2="(pickerX2/10) + '%'" 
                                :y2="(pickerY2/10) + '%'" 
                                stroke="white" 
                                stroke-width="2"
                                stroke-dasharray="8,4"
                            />
                        </svg>
                    </div>
                    
                    <!-- Coordinate display -->
                    <div class="mt-3 text-center text-sm text-gray-600 dark:text-gray-400">
                        <div v-if="coordPickerMode === 'single'">
                            坐标: <span class="font-mono font-bold text-green-500">{{ pickerX1 }}, {{ pickerY1 }}</span>
                        </div>
                        <div v-else class="flex justify-center gap-4">
                            <span>起点: <span class="font-mono font-bold text-green-500">{{ pickerX1 }}, {{ pickerY1 }}</span></span>
                            <span>终点: <span class="font-mono font-bold text-red-500">{{ pickerX2 }}, {{ pickerY2 }}</span></span>
                        </div>
                    </div>
                    <p class="mt-1 text-xs text-gray-400 text-center">拖动圆点调整位置 (范围: 0-1000)</p>
                </div>
                
                <div class="p-4 border-t flex gap-3 bg-gray-50 border-gray-200 dark:bg-[#252525] dark:border-white/5">
                    <button @click="showCoordPicker = false" class="flex-1 py-2 rounded-lg bg-gray-200 hover:bg-gray-300 text-gray-800 dark:bg-gray-700 dark:hover:bg-gray-600 dark:text-white text-sm font-medium transition-colors">取消</button>
                    <button @click="applyPickedCoords" class="flex-1 py-2 rounded-lg bg-[#00C853] hover:bg-[#00E676] text-white dark:text-black text-sm font-bold transition-colors">应用</button>
                </div>
            </div>
        </div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick, computed, watch } from 'vue'
import Bridge from './Bridge'

// --- Types ---
interface LogEntry {
  timestamp: number
  type: string
  message: string
  id?: string // Added ID support
}

interface ApiConfig {
    id: string
    name: string
    provider: string
    model: string
    apiKey: string
    customEndpoint?: string
    enabled: boolean
    priority: number
}

// Chat message types for conversational UI
interface ChatMessage {
    id: string
    role: 'user' | 'assistant' | 'system'
    content: string
    timestamp: number
    status?: 'thinking' | 'executing' | 'done' | 'error'
    thinkContent?: string  // For collapsible think section
    action?: string        // Action description
}

interface ChatSession {
    id: string
    title: string
    messages: ChatMessage[]
    createdAt: number
}

interface TaskList {
    id: string
    name: string
    script: string
    createdAt: number
}

// --- Data ---
const providers = [
  { value: 'ZHIPU', label: '智谱 AI' },
  { value: 'OPENAI', label: 'OpenAI' },
  { value: 'CLAUDE', label: 'Anthropic Claude' },
  { value: 'GEMINI', label: 'Google Gemini' },
  { value: 'QWEN', label: '通义千问' },
  { value: 'DEEPSEEK', label: 'DeepSeek' },
  { value: 'MOONSHOT', label: 'Moonshot (Kimi)' },
  { value: 'OPENAI_COMPATIBLE', label: 'OpenAI 兼容接口' }
]

const providerModels: Record<string, {id: string, name: string}[]> = {
    'ZHIPU': [
        { id: 'glm-4v', name: 'GLM-4V (推荐)' },
        { id: 'glm-4v-plus', name: 'GLM-4V-Plus' }
    ],
    'OPENAI': [
        { id: 'gpt-4o', name: 'GPT-4o (推荐)' },
        { id: 'gpt-4o-mini', name: 'GPT-4o Mini' },
        { id: 'gpt-4-turbo', name: 'GPT-4 Turbo' }
    ],
    'CLAUDE': [
        { id: 'claude-3-5-sonnet-20241022', name: 'Claude 3.5 Sonnet (推荐)' },
        { id: 'claude-3-opus-20240229', name: 'Claude 3 Opus' },
        { id: 'claude-3-haiku-20240307', name: 'Claude 3 Haiku' }
    ],
    'GEMINI': [
        { id: 'gemini-1.5-flash', name: 'Gemini 1.5 Flash (推荐)' },
        { id: 'gemini-1.5-pro', name: 'Gemini 1.5 Pro' },
         { id: 'gemini-2.0-flash-exp', name: 'Gemini 2.0 Flash' }
    ],
    'QWEN': [
        { id: 'qwen-vl-max', name: 'Qwen-VL-Max' },
        { id: 'qwen-vl-plus', name: 'Qwen-VL-Plus' }
    ],
    'DEEPSEEK': [
        { id: 'deepseek-chat', name: 'DeepSeek Chat' }
    ],
    'MOONSHOT': [
        { id: 'moonshot-v1-128k', name: 'Moonshot V1 128K' }
    ]
}

// --- State ---
const isDark = ref(true) // Default to dark
const taskSchema = ref('')
const logs = ref<LogEntry[]>([])
const isRunning = ref(false)
const isPaused = ref(false)
const serviceEnabled = ref(false)
const overlayPermissionValid = ref(false)
const logContainer = ref<HTMLElement | null>(null)
const commandHistory = ref<string[]>([])

// Execution mode and Shizuku state
const savedMode = Bridge.getExecutionMode()
const executionMode = ref<'accessibility' | 'shizuku'>(savedMode === 'shizuku' ? 'shizuku' : 'accessibility')
const setMode = (mode: 'accessibility' | 'shizuku') => {
  executionMode.value = mode
  Bridge.setExecutionMode(mode)
}
const shizukuStatus = ref({
    available: false,
    hasPermission: false,
    serviceBound: false,
    uid: -1,
    privilege: 'UNKNOWN'
})

// Computed: check if permission setup is needed
const needsPermissionSetup = computed(() => {
    if (executionMode.value === 'accessibility') {
        return !serviceEnabled.value
    } else {
        return !shizukuStatus.value.available || !shizukuStatus.value.hasPermission
    }
})

// Helper: truncate long command text
const truncateCommand = (cmd: string, maxLength = 40): string => {
    if (cmd.length <= maxLength) return cmd
    return cmd.substring(0, maxLength) + '...'
}

// Settings State
const showSettings = ref(false)
const showHistoryDropdown = ref(false)

// SoM Preview State
const showSomPreview = ref(false)
const somPreviewLoading = ref(false)
const somPreviewImage = ref('')

// File Log State
const showFileLog = ref(false)
const fileLogLoading = ref(false)
const fileLogContent = ref('')

// Custom Confirm Modal State
const showConfirmModal = ref(false)
const confirmMessage = ref('')
const confirmCallback = ref<(() => void) | null>(null)

// Task List State
const customTaskLists = ref<TaskList[]>([])
const showTaskManager = ref(false)
const showTaskEditor = ref(false)
const showImportModal = ref(false)
const editingList = ref<TaskList>({
    id: '',
    name: '',
    script: '',
    createdAt: 0
})
const importSelection = ref<{sessionId: string, selectedIndices: Set<number>}>({
    sessionId: '',
    selectedIndices: new Set()
})

// Command type definitions
interface ScriptCommand {
    type: string
    param1: string
    param2: string
    param3: string
    param4: string
}

const commandTypes = [
    { id: 'tap', label: '点击', params: ['X坐标', 'Y坐标'], needsCoord: true },
    { id: 'doubletap', label: '双击', params: ['X坐标', 'Y坐标'], needsCoord: true },
    { id: 'swipe', label: '滑动', params: ['起始X', '起始Y', '结束X', '结束Y'], needsCoord: true },
    { id: 'longpress', label: '长按', params: ['X坐标', 'Y坐标'], needsCoord: true },
    { id: 'wait', label: '等待', params: ['毫秒'], needsCoord: false },
    { id: 'type', label: '输入文字', params: ['文本内容'], needsCoord: false },
    { id: 'launch', label: '启动应用', params: ['应用包名或名称'], needsCoord: false },
    { id: 'back', label: '返回', params: [], needsCoord: false },
    { id: 'home', label: '主屏幕', params: [], needsCoord: false },
    { id: 'enter', label: '回车', params: [], needsCoord: false },
    { id: 'keyevent', label: '按键', params: ['键码'], needsCoord: false },
    { id: 'shell', label: 'Shell命令', params: ['命令'], needsCoord: false },
    // Loop and repeat commands
    { id: 'loop', label: '🔄 循环执行', params: ['次数'], needsCoord: false },
    { id: 'loop_time', label: '⏱️ 定时循环', params: ['毫秒'], needsCoord: false },
    { id: 'repeat', label: '🔁 重复下条', params: ['次数'], needsCoord: false },
    { id: 'repeat_start', label: '📍 重复开始', params: [], needsCoord: false },
    { id: 'repeat_end', label: '📍 重复结束', params: ['次数或时间ms'], needsCoord: false },
]

// Structured commands for editing
const editingCommands = ref<ScriptCommand[]>([])

// Coordinate Picker State
const showCoordPicker = ref(false)
const coordPickerIndex = ref(-1) // Which command is being edited
const coordPickerMode = ref<'single' | 'swipe'>('single')
const pickerX1 = ref(500)
const pickerY1 = ref(500)
const pickerX2 = ref(500)
const pickerY2 = ref(800)

// Scratch-style block collapse state
const collapsedBlocks = ref<Record<number, boolean>>({})

const toggleBlockCollapsed = (idx: number) => {
    collapsedBlocks.value[idx] = !collapsedBlocks.value[idx]
}

const getLoopBlockClass = (type: string) => {
    return '' // Additional wrapper classes if needed
}

const getLoopHeaderClass = (type: string) => {
    if (type === 'loop') return 'bg-amber-600 hover:bg-amber-500'
    if (type === 'loop_time') return 'bg-orange-600 hover:bg-orange-500'
    return 'bg-gray-600'
}

const getLoopContentBorderClass = (type: string) => {
    if (type === 'loop') return 'border-amber-400'
    if (type === 'loop_time') return 'border-orange-400'
    return 'border-gray-400'
}

const getCommandNestingClass = (idx: number) => {
    // Check if this command is inside a repeat_start/repeat_end block
    // Note: loop and loop_time are single-line commands, not block markers
    let nestingLevel = 0
    for (let i = 0; i < idx; i++) {
        const cmd = editingCommands.value[i]
        if (cmd.type === 'repeat_start') {
            nestingLevel++
        } else if (cmd.type === 'repeat_end') {
            nestingLevel = Math.max(0, nestingLevel - 1)
        }
    }
    
    if (nestingLevel > 0) {
        // Strong visual nesting: left indent, purple left border and background
        return 'ml-6 pl-2 border-l-4 border-purple-500 bg-purple-100/50 dark:bg-purple-900/30 rounded-l-none'
    }
    return ''
}

const needsCoordinates = (type: string) => {
    const cmd = commandTypes.find(c => c.id === type)
    return cmd?.needsCoord ?? false
}

// Command manipulation functions
const addScriptCommand = () => {
    editingCommands.value.push({
        type: 'tap',
        param1: '',
        param2: '',
        param3: '',
        param4: ''
    })
}

const removeCommand = (idx: number) => {
    editingCommands.value.splice(idx, 1)
}

const moveCommandUp = (idx: number) => {
    if (idx > 0) {
        const temp = editingCommands.value[idx]
        editingCommands.value[idx] = editingCommands.value[idx - 1]
        editingCommands.value[idx - 1] = temp
    }
}

const moveCommandDown = (idx: number) => {
    if (idx < editingCommands.value.length - 1) {
        const temp = editingCommands.value[idx]
        editingCommands.value[idx] = editingCommands.value[idx + 1]
        editingCommands.value[idx + 1] = temp
    }
}

const getCommandParams = (type: string) => {
    return commandTypes.find(c => c.id === type)?.params || []
}

// Convert structured command to script line
const commandToScript = (cmd: ScriptCommand): string => {
    const params = [cmd.param1, cmd.param2, cmd.param3, cmd.param4].filter(p => p.trim())
    if (['tap', 'doubletap', 'longpress'].includes(cmd.type)) {
        return `#${cmd.type} ${cmd.param1},${cmd.param2}`
    } else if (cmd.type === 'swipe') {
        return `#swipe ${cmd.param1},${cmd.param2},${cmd.param3},${cmd.param4}`
    } else if (['back', 'home', 'enter', 'repeat_start'].includes(cmd.type)) {
        return `#${cmd.type}`
    } else if (['wait', 'loop', 'loop_time', 'repeat', 'repeat_end'].includes(cmd.type)) {
        return `#${cmd.type} ${cmd.param1}`
    } else {
        return `#${cmd.type} ${params.join(' ')}`
    }
}

// Parse script line to structured command
const scriptToCommand = (line: string): ScriptCommand => {
    const cmd: ScriptCommand = { type: 'tap', param1: '', param2: '', param3: '', param4: '' }
    if (!line.startsWith('#')) return cmd
    
    const parts = line.substring(1).split(' ', 2)
    cmd.type = parts[0].toLowerCase()
    const args = parts[1] || ''
    
    if (['tap', 'doubletap', 'longpress'].includes(cmd.type)) {
        const coords = args.split(',')
        cmd.param1 = coords[0]?.trim() || ''
        cmd.param2 = coords[1]?.trim() || ''
    } else if (cmd.type === 'swipe') {
        const coords = args.split(',')
        cmd.param1 = coords[0]?.trim() || ''
        cmd.param2 = coords[1]?.trim() || ''
        cmd.param3 = coords[2]?.trim() || ''
        cmd.param4 = coords[3]?.trim() || ''
    } else if (['back', 'home', 'enter', 'repeat_start'].includes(cmd.type)) {
        // No params
    } else {
        // For wait, loop, loop_time, repeat, repeat_end, type, launch, etc.
        cmd.param1 = args
    }
    
    return cmd
}

// Open coordinate picker for a command
const openCoordPicker = (idx: number) => {
    const cmd = editingCommands.value[idx]
    coordPickerIndex.value = idx
    
    const x1 = parseInt(cmd.param1) || 500
    const y1 = parseInt(cmd.param2) || 500
    const x2 = parseInt(cmd.param3) || 500
    const y2 = parseInt(cmd.param4) || 800
    const mode = cmd.type === 'swipe' ? 'swipe' : 'single'
    
    // Use native overlay picker via Bridge
    Bridge.startCoordPicker(
        mode,
        x1,
        y1,
        x2,
        y2,
        (result) => {
            // Apply picked coordinates back to command
            const editCmd = editingCommands.value[idx]
            if (!editCmd) return
            
            editCmd.param1 = String(result.x1)
            editCmd.param2 = String(result.y1)
            
            if (mode === 'swipe' && result.x2 !== undefined && result.y2 !== undefined) {
                editCmd.param3 = String(result.x2)
                editCmd.param4 = String(result.y2)
            }
        },
        () => {
            // Cancelled - do nothing
        }
    )
}

// Apply picked coordinates back to command
const applyPickedCoords = () => {
    const idx = coordPickerIndex.value
    if (idx < 0 || idx >= editingCommands.value.length) return
    
    const cmd = editingCommands.value[idx]
    cmd.param1 = String(pickerX1.value)
    cmd.param2 = String(pickerY1.value)
    
    if (cmd.type === 'swipe') {
        cmd.param3 = String(pickerX2.value)
        cmd.param4 = String(pickerY2.value)
    }
    
    showCoordPicker.value = false
}

// Picker area reference
const pickerArea = ref<HTMLElement | null>(null)
const draggingMarker = ref<'start' | 'end' | null>(null)

// Handle clicks on the picker area to position marker
const handlePickerClick = (event: MouseEvent) => {
    if (!pickerArea.value || draggingMarker.value) return
    
    const rect = pickerArea.value.getBoundingClientRect()
    const x = Math.round(((event.clientX - rect.left) / rect.width) * 1000)
    const y = Math.round(((event.clientY - rect.top) / rect.height) * 1000)
    
    // Clamp to 0-1000
    const clampedX = Math.max(0, Math.min(1000, x))
    const clampedY = Math.max(0, Math.min(1000, y))
    
    if (coordPickerMode.value === 'single') {
        pickerX1.value = clampedX
        pickerY1.value = clampedY
    }
}

// Start dragging a marker
const startDrag = (event: MouseEvent | TouchEvent, marker: 'start' | 'end') => {
    event.preventDefault()
    draggingMarker.value = marker
    
    const moveHandler = (e: MouseEvent | TouchEvent) => {
        if (!pickerArea.value || !draggingMarker.value) return
        
        const rect = pickerArea.value.getBoundingClientRect()
        const clientX = 'touches' in e ? e.touches[0].clientX : e.clientX
        const clientY = 'touches' in e ? e.touches[0].clientY : e.clientY
        
        const x = Math.round(((clientX - rect.left) / rect.width) * 1000)
        const y = Math.round(((clientY - rect.top) / rect.height) * 1000)
        
        // Clamp to 0-1000
        const clampedX = Math.max(0, Math.min(1000, x))
        const clampedY = Math.max(0, Math.min(1000, y))
        
        if (draggingMarker.value === 'start') {
            pickerX1.value = clampedX
            pickerY1.value = clampedY
        } else {
            pickerX2.value = clampedX
            pickerY2.value = clampedY
        }
    }
    
    const upHandler = () => {
        draggingMarker.value = null
        document.removeEventListener('mousemove', moveHandler)
        document.removeEventListener('mouseup', upHandler)
        document.removeEventListener('touchmove', moveHandler)
        document.removeEventListener('touchend', upHandler)
    }
    
    document.addEventListener('mousemove', moveHandler)
    document.addEventListener('mouseup', upHandler)
    document.addEventListener('touchmove', moveHandler)
    document.addEventListener('touchend', upHandler)
}

// Task Runner State
const isRunningList = ref(false)
const currentRunningListId = ref<string | null>(null)
const currentRunningIndex = ref(-1)
const processingListQueue = ref(false)

const apiConfigs = ref<ApiConfig[]>([])
const showConfigModal = ref(false)
const isEditing = ref(false)
const form = ref({
    id: '',
    name: '',
    provider: 'ZHIPU',
    selectedModel: '',
    customModel: '',
    apiKey: '',
    customEndpoint: '',
    enabled: true,
    priority: 0
})

// Chat UI State
const chatMessages = ref<ChatMessage[]>([])
const chatSessions = ref<ChatSession[]>([])
const currentSessionId = ref<string | null>(null)
const showChatHistory = ref(false)
const expandedThinks = ref<Set<string>>(new Set())  // Track which think sections are expanded
const editingSessionId = ref<string | null>(null)
const renameTitle = ref('')
const renameInput = ref<HTMLInputElement[] | null>(null)

// Aggregate logs into conversation turns
interface ConversationTurn {
  id: string
  userTask: string
  userTimestamp: number
  aiResponses: { type: string; message: string; timestamp: number }[]
  isComplete: boolean
}

const conversationTurns = computed<ConversationTurn[]>(() => {
  const turns: ConversationTurn[] = []
  let currentTurn: ConversationTurn | null = null
  let currentAiResponse: { type: string; message: string; timestamp: number }[] = []
  
  const flushAiResponse = () => {
    if (currentTurn && currentAiResponse.length > 0) {
      currentTurn.aiResponses.push(...currentAiResponse)
      currentAiResponse = []
    }
  }
  
  for (const log of logs.value) {
    if (log.type === 'INFO' && log.message.includes('开始执行任务')) {
      flushAiResponse()
      if (currentTurn) {
        currentTurn.isComplete = true
        turns.push(currentTurn)
      }
      currentTurn = {
        id: `turn-${log.timestamp}`,
        userTask: log.message.replace('开始执行任务:', '').trim() || '执行任务',
        userTimestamp: log.timestamp,
        aiResponses: [],
        isComplete: false
      }
      continue
    }
    
    if (!currentTurn) continue
    
    // Strict Filtering (Match Floating Window)
    // Only allow: AI Think, Action, Command (Input), Error, Warning, specific status
    const isRelevant = 
      log.message.includes('AI 思考') || 
      log.message.includes('AI Think') || 
      log.message.includes('分析') ||
      log.message.startsWith('执行') || 
      log.message.startsWith('指令:') ||
      log.type === 'ACTION' || 
      log.type === 'ERROR' ||
      log.type === 'WARNING' ||
      log.message.includes('任务完成') || 
      log.message.includes('任务停止');

    if (!isRelevant) {
      continue
    }
    
    // AI thinking starts a new response cycle (but we group think+action together)
    if (log.message && log.message.trim()) {
      currentAiResponse.push({
        type: log.type,
        message: log.message,
        timestamp: log.timestamp
      })
      
      // Action or Error typically completes a thought bubble sequence
      if (log.type === 'ACTION' || log.type === 'ERROR' || log.message.startsWith('执行')) {
        flushAiResponse()
      }
    }
    
    if (log.message.includes('任务完成') || log.message.includes('任务停止') || log.type === 'ERROR') {
      flushAiResponse()
      currentTurn.isComplete = true
    }
  }
  
  flushAiResponse()
  if (currentTurn) {
    turns.push(currentTurn)
  }
  
  return turns
})

// Settings State (Log Level, Dev Mode)
const logLevel = ref(3)  // 0=OFF, 1=ERROR, 2=WARNING, 3=INFO, 4=DEBUG
const maxSteps = ref(50)
const devMode = ref(false)
const showLogViewer = ref(false)  // For log viewer modal

// Log level options
const logLevelOptions = [
    { value: 0, label: '关闭' },
    { value: 1, label: '仅错误' },
    { value: 2, label: '警告' },
    { value: 3, label: '信息' },
    { value: 4, label: '调试' }
]

// --- Computed ---
const currentModels = computed(() => {
    return providerModels[form.value.provider] || []
})

// --- Actions (Theme) ---
const toggleTheme = () => {
    isDark.value = !isDark.value
}

watch(isDark, (val) => {
    if (val) {
        document.documentElement.classList.add('dark')
        localStorage.setItem('theme', 'dark')
    } else {
        document.documentElement.classList.remove('dark')
        localStorage.setItem('theme', 'light')
    }
}, { immediate: true })


// --- Actions (Main) ---

const startTask = () => {
  if (!taskSchema.value) return
  
  if (executionMode.value === 'accessibility') {
    if (!serviceEnabled.value) { 
      showCustomConfirm('请先开启无障碍服务', () => { openAccessibility() })
      return 
    }
  } else {
    if (!shizukuStatus.value.available) { 
      showCustomConfirm('请先启动 Shizuku 应用', () => {})
      return 
    }
    if (!shizukuStatus.value.hasPermission) { 
      showCustomConfirm('请先授权 Shizuku 权限', () => { requestShizukuPermission() })
      return 
    }
    // 自动绑定 UserService
    if (!shizukuStatus.value.serviceBound) {
      bindShizukuService()
    }
  }
  
  // Save user's task to chat history
  addUserMessage(taskSchema.value)
  
  Bridge.startTaskWithMode(taskSchema.value, executionMode.value)
}

const stopTask = () => Bridge.stopTask()
const togglePause = () => Bridge.togglePause()
const clearLogs = () => { logs.value = [] }
const openAccessibility = () => Bridge.openAccessibilitySettings()

const requestOverlayPermission = () => {
    Bridge.requestOverlayPermission()
    const check = setInterval(() => {
        const hasPerm = Bridge.checkOverlayPermission()
        overlayPermissionValid.value = hasPerm
        if (hasPerm) clearInterval(check)
    }, 1000)
    setTimeout(() => clearInterval(check), 10000)
}

const checkPermissions = () => {
    overlayPermissionValid.value = Bridge.checkOverlayPermission()
}

// --- SoM Preview ---

const loadSomPreview = () => {
    somPreviewLoading.value = true
    somPreviewImage.value = ''
    try {
        const base64Data = Bridge.getSomPreview()
        if (base64Data && base64Data.length > 0) {
            somPreviewImage.value = `data:image/png;base64,${base64Data}`
            showSomPreview.value = true
        } else {
            console.log('No SoM preview available')
        }
    } catch (e) {
        console.error('Failed to get SoM preview', e)
    } finally {
        somPreviewLoading.value = false
    }
}

// --- File Log Actions ---

const loadFileLog = () => {
    fileLogLoading.value = true
    try {
        fileLogContent.value = Bridge.getFileLogContent()
        showFileLog.value = true
    } catch (e) {
        console.error('Failed to load file log', e)
        fileLogContent.value = '读取日志文件失败'
    } finally {
        fileLogLoading.value = false
    }
}

// --- Shizuku Actions ---

const checkShizukuStatus = () => {
    try {
        shizukuStatus.value = Bridge.getShizukuStatus()
    } catch (e) {
        console.error('Failed to get Shizuku status', e)
    }
}

const requestShizukuPermission = () => {
    Bridge.requestShizukuPermission()
    // Re-check status after a delay
    setTimeout(checkShizukuStatus, 1000)
}

const bindShizukuService = () => {
    Bridge.bindShizukuService()
    // Re-check status after a delay
    setTimeout(checkShizukuStatus, 1000)
}

const loadCommandHistory = () => {
    try {
        commandHistory.value = Bridge.getCommandHistory()
    } catch (e) {
        console.error("Failed to load command history", e)
        commandHistory.value = []
    }
}

const selectHistoryCommand = (command: string) => {
    if (command) {
        taskSchema.value = command
    }
}

const clearHistory = () => {
    showCustomConfirm('确定要清除所有历史记录吗？', () => {
        Bridge.clearCommandHistory()
        commandHistory.value = []
        showHistoryDropdown.value = false
    })
}

// --- Message Actions ---

const copyMessage = async (content: string) => {
    try {
        await navigator.clipboard.writeText(content)
        // Ideally show a toast here, for now a console log
        console.log('Copied to clipboard')
    } catch(err) {
        console.error('Failed to copy', err)
    }
}

const deleteMessage = (msgId: string) => {
    showCustomConfirm('确定要删除这条消息吗？', () => {
        const idx = chatMessages.value.findIndex(m => m.id === msgId)
        if (idx !== -1) {
            chatMessages.value.splice(idx, 1)
            saveChatToSession()
            // Refresh logs view
            updateLogsFromChat() 
        }
    })
}

// --- Session Actions ---

const deleteChatSession = (sessionId: string) => {
    showCustomConfirm('确定要删除个对话吗？', () => {
        const idx = chatSessions.value.findIndex(s => s.id === sessionId)
        if (idx !== -1) {
            chatSessions.value.splice(idx, 1)
            Bridge.saveChatHistory(chatSessions.value)
            
            // If deleting current session, clear view
            if (sessionId === currentSessionId.value) {
                startNewChat()
            }
        }
    })
}

// Rename Logic
const startRenameSession = (session: ChatSession) => {
    editingSessionId.value = session.id
    renameTitle.value = session.title
    nextTick(() => {
        if (renameInput.value && renameInput.value[0]) {
            renameInput.value[0].focus()
        }
    })
}

const saveRenameSession = (session: ChatSession) => {
    if (!renameTitle.value.trim()) return
    
    session.title = renameTitle.value.trim()
    editingSessionId.value = null
    
    // Update list and persist
    const idx = chatSessions.value.findIndex(s => s.id === session.id)
    if (idx !== -1) {
        chatSessions.value[idx] = session
        Bridge.saveChatHistory(chatSessions.value)
    }
}

const cancelRenameSession = () => {
    editingSessionId.value = null
    renameTitle.value = ''
}

// Helper to update logs wrapper
const updateLogsFromChat = () => {
    logs.value = chatMessages.value.map(msg => ({
        timestamp: msg.timestamp,
        type: msg.role === 'user' ? 'INFO' : (msg.status === 'error' ? 'ERROR' : 'AI'),
        message: msg.role === 'user' 
            ? `开始执行任务: ${msg.content}` 
            : (msg.thinkContent ? `[思考] ${msg.thinkContent}\n${msg.content}` : msg.content),
        // Add ID for mapping back if needed, though LogEntry doesn't have it currently
        // We can just rely on the message content for display
        id: msg.id 
    })) as any // Cast to satisfy LogEntry type
}

// Custom Confirm Modal Functions
const showCustomConfirm = (message: string, onConfirm: () => void) => {
    confirmMessage.value = message
    confirmCallback.value = onConfirm
    showConfirmModal.value = true
}

const handleConfirm = () => {
    if (confirmCallback.value) {
        confirmCallback.value()
    }
    showConfirmModal.value = false
    confirmCallback.value = null
}

const handleCancel = () => {
    showConfirmModal.value = false
    confirmCallback.value = null
}

// --- Chat Actions ---

const addUserMessage = (content: string) => {
    const msg: ChatMessage = {
        id: crypto.randomUUID(),
        role: 'user',
        content,
        timestamp: Date.now()
    }
    chatMessages.value.push(msg)
    saveChatToSession()
}

const addAssistantMessage = (content: string, thinkContent?: string, status: 'thinking' | 'executing' | 'done' | 'error' = 'done') => {
    const msg: ChatMessage = {
        id: crypto.randomUUID(),
        role: 'assistant',
        content,
        timestamp: Date.now(),
        status,
        thinkContent
    }
    chatMessages.value.push(msg)
    saveChatToSession()
}

const toggleThinkExpand = (msgId: string) => {
    if (expandedThinks.value.has(msgId)) {
        expandedThinks.value.delete(msgId)
    } else {
        expandedThinks.value.add(msgId)
    }
}

const startNewChat = () => {
    chatMessages.value = []
    currentSessionId.value = null
    // Clear logs to reset UI display
    logs.value = []
}

const saveChatToSession = () => {
    if (chatMessages.value.length === 0) return
    
    // Create or update session
    const sessionId = currentSessionId.value || crypto.randomUUID()
    
    // Determine title: Use existing title if session exists (preserve rename), else use first message
    let title = '新对话'
    const existingIdx = chatSessions.value.findIndex(s => s.id === sessionId)
    
    if (existingIdx >= 0) {
        title = chatSessions.value[existingIdx].title // Preserve existing title
    } else {
        title = chatMessages.value[0]?.content?.substring(0, 30) || '新对话'
    }
    
    const session: ChatSession = {
        id: sessionId,
        title,
        messages: [...chatMessages.value],
        createdAt: Date.now()
    }
    
    // Update or add session
    if (existingIdx >= 0) {
        chatSessions.value[existingIdx] = session
    } else {
        chatSessions.value.unshift(session)
    }
    
    currentSessionId.value = sessionId
    
    // Persist to native
    Bridge.saveChatHistory(chatSessions.value)
}

const loadChatSession = (session: ChatSession) => {
    chatMessages.value = [...session.messages]
    currentSessionId.value = session.id
    showChatHistory.value = false
    
    // Convert chatMessages to logs format for UI display
    updateLogsFromChat()
}



// --- Settings Actions ---

const loadSettings = () => {
    logLevel.value = Bridge.getLogLevel()
    devMode.value = Bridge.getDevMode()
    maxSteps.value = Bridge.getMaxSteps()
    chatSessions.value = Bridge.getChatHistory()
}

const saveLogLevel = (level: number) => {
    logLevel.value = level
    Bridge.setLogLevel(level)
}

const saveMaxSteps = (steps: number) => {
    // Validate range
    let val = steps
    if (val < 10) val = 10
    if (val > 200) val = 200
    
    maxSteps.value = val
    Bridge.setMaxSteps(val)
}

const toggleDevMode = () => {
    devMode.value = !devMode.value
    Bridge.setDevMode(devMode.value)
}

// --- Actions (Settings) ---

const loadApiConfigs = () => {
    try {
        const configs = Bridge.getApiConfigs()
        apiConfigs.value = Array.isArray(configs) ? configs : []
    } catch (e) {
        console.error("Failed to load configs", e)
        apiConfigs.value = []
    }
}

const openAddDialog = () => {
    isEditing.value = false
    form.value = {
        id: crypto.randomUUID(),
        name: '',
        provider: 'ZHIPU',
        selectedModel: 'glm-4v',
        customModel: '',
        apiKey: '',
        customEndpoint: '',
        enabled: true,
        priority: 0
    }
    showConfigModal.value = true
}

const editConfig = (config: ApiConfig) => {
    isEditing.value = true
    const models = providerModels[config.provider] || []
    const isStandardModel = models.some(m => m.id === config.model)
    
    form.value = {
        id: config.id,
        name: config.name,
        provider: config.provider,
        selectedModel: isStandardModel ? config.model : 'custom',
        customModel: isStandardModel ? '' : config.model,
        apiKey: config.apiKey,
        customEndpoint: config.customEndpoint || '',
        enabled: config.enabled,
        priority: config.priority
    }
    showConfigModal.value = true
}

const deleteConfig = (config: ApiConfig) => {
    if (confirm(`确定要删除 "${config.name}" 吗?`)) {
        Bridge.deleteApiConfig(config.id)
        loadApiConfigs()
    }
}

const toggleConfig = (config: ApiConfig) => {
    config.enabled = !config.enabled
    Bridge.saveApiConfig(config) // Save full object update
    loadApiConfigs()
}

const saveForm = () => {
    if (!form.value.apiKey) {
        alert("请输入 Apikey")
        return
    }
    
    const finalModel = form.value.selectedModel === 'custom' ? form.value.customModel : form.value.selectedModel
    if (!finalModel) {
        alert("请输入或选择模型")
        return
    }

    const config: ApiConfig = {
        id: form.value.id,
        name: form.value.name || (isEditing.value ? form.value.name : '我的 API'),
        provider: form.value.provider,
        model: finalModel,
        apiKey: form.value.apiKey,
        customEndpoint: form.value.provider === 'OPENAI_COMPATIBLE' ? form.value.customEndpoint : undefined,
        enabled: form.value.enabled,
        priority: form.value.priority
    }
    
    Bridge.saveApiConfig(config)
    showConfigModal.value = false
    loadApiConfigs()
}

const onProviderChange = () => {
    const models = currentModels.value
    if (models.length > 0) {
        form.value.selectedModel = models[0].id
    } else {
        form.value.selectedModel = 'custom'
    }
}

const onModelSelectChange = () => {
    if (form.value.selectedModel !== 'custom') {
        form.value.customModel = ''
    }
}

// --- Task List Actions ---

const loadTaskLists = () => {
    try {
        const lists = Bridge.getTaskLists()
        customTaskLists.value = Array.isArray(lists) ? lists : []
    } catch (e) {
        console.error("Failed to load task lists", e)
        customTaskLists.value = []
    }
}

const saveTaskListsHelper = () => {
    Bridge.saveTaskLists(customTaskLists.value)
}

const openTaskManager = () => {
    loadTaskLists()
    showTaskManager.value = true
}

const createNewTaskList = () => {
    editingList.value = {
        id: crypto.randomUUID(),
        name: '新任务列表',
        script: '',
        createdAt: Date.now()
    }
    editingCommands.value = [] // Reset commands array
    showTaskEditor.value = true
}

const editTaskList = (list: TaskList) => {
    editingList.value = JSON.parse(JSON.stringify(list))
    // Parse script into structured commands for card-based editing
    editingCommands.value = parseScriptLines(list.script).map(scriptToCommand)
    showTaskEditor.value = true
}

const deleteTaskList = (id: string) => {
    showCustomConfirm('确定要删除这个任务列表吗？', () => {
        const idx = customTaskLists.value.findIndex(l => l.id === id)
        if (idx !== -1) {
            customTaskLists.value.splice(idx, 1)
            saveTaskListsHelper()
        }
    })
}

// Export task list to file
const exportTaskList = (list: TaskList) => {
    const content = `# ${list.name}\n# Created: ${new Date(list.createdAt).toLocaleString()}\n\n${list.script}`
    const filename = `${list.name.replace(/[^a-zA-Z0-9\u4e00-\u9fa5]/g, '_')}.txt`
    Bridge.exportToFile(filename, content)
}

// Import task list from file
const importFromFile = () => {
    Bridge.importFromFile()
}

// Callback for when file is imported (called from native)
if (typeof window !== 'undefined') {
    ;(window as any).onFileImported = (filename: string, content: string) => {
        // Parse filename to get name (remove extension)
        const name = filename.replace(/\.[^.]+$/, '') || '导入的任务'
        
        // Create new task list
        const newList: TaskList = {
            id: crypto.randomUUID(),
            name: name,
            script: content.split('\n')
                .filter(l => !l.startsWith('# ') || l.startsWith('#tap') || l.startsWith('#swipe'))
                .map(l => l.trim())
                .filter(l => l.length > 0 && l.startsWith('#'))
                .join('\n'),
            createdAt: Date.now()
        }
        
        // If no valid commands found, use the raw content
        if (!newList.script.trim()) {
            newList.script = content
        }
        
        customTaskLists.value.push(newList)
        saveTaskListsHelper()
        
        // Show toast or message
        alert(`成功导入: ${name}`)
    }
}

const saveTaskList = () => {
    if (!editingList.value.name.trim()) {
        alert('请输入任务列表名称')
        return
    }
    
    // Sync structured commands back to script string
    editingList.value.script = editingCommands.value
        .map(commandToScript)
        .filter(s => s.length > 1) // Filter out empty commands like just "#"
        .join('\n')
    
    const idx = customTaskLists.value.findIndex(l => l.id === editingList.value.id)
    if (idx !== -1) {
        customTaskLists.value[idx] = editingList.value
    } else {
        customTaskLists.value.push(editingList.value)
    }
    
    saveTaskListsHelper()
    showTaskEditor.value = false
}

// Execution Logic
// Loop execution state
const loopCount = ref(0)
const loopMax = ref(0)
const loopStartTime = ref(0)
const loopDuration = ref(0)
const repeatCount = ref(0)
const repeatMax = ref(0)
const repeatStartIndex = ref(-1)
const repeatStartTime = ref(0)
const repeatDuration = ref(0)

const runTaskList = (list: TaskList) => {
    const lines = parseScriptLines(list.script)
    if (lines.length === 0) return
    
    showTaskManager.value = false
    isRunningList.value = true
    currentRunningListId.value = list.id
    currentRunningIndex.value = 0
    processingListQueue.value = true
    
    // Reset loop state
    loopCount.value = 0
    loopMax.value = 0
    loopStartTime.value = 0
    loopDuration.value = 0
    repeatCount.value = 0
    repeatMax.value = 0
    repeatStartIndex.value = -1
    repeatStartTime.value = 0
    repeatDuration.value = 0
    
    // Check for #loop or #loop_time at start
    if (lines.length > 0) {
        const firstCmd = scriptToCommand(lines[0])
        if (firstCmd.type === 'loop') {
            loopMax.value = parseInt(firstCmd.param1) || 1
            loopCount.value = 1
            addAssistantMessage(`🔄 开始循环执行 (第 1/${loopMax.value} 次)`, undefined, 'executing')
        } else if (firstCmd.type === 'loop_time') {
            loopDuration.value = parseInt(firstCmd.param1) || 60000
            loopStartTime.value = Date.now()
            loopCount.value = 1
            addAssistantMessage(`⏱️ 开始定时循环 (${loopDuration.value}ms)`, undefined, 'executing')
        }
    }
    
    // Start first command
    executeNextTaskInList(list, lines)
}

const executeNextTaskInList = async (list: TaskList, lines: string[]) => {
    if (!isRunningList.value) return
    
    let idx = currentRunningIndex.value
    
    // Skip control commands (loop, loop_time at start)
    if (idx === 0) {
        const cmd = scriptToCommand(lines[0])
        if (['loop', 'loop_time'].includes(cmd.type)) {
            currentRunningIndex.value = 1
            idx = 1
        }
    }
    
    if (idx >= lines.length) {
        // End of script - check if we need to loop
        if (loopMax.value > 0 && loopCount.value < loopMax.value) {
            loopCount.value++
            currentRunningIndex.value = 1 // Skip #loop command
            addAssistantMessage(`🔄 循环执行 (第 ${loopCount.value}/${loopMax.value} 次)`, undefined, 'executing')
            setTimeout(() => executeNextTaskInList(list, lines), 300)
            return
        }
        
        if (loopDuration.value > 0) {
            const elapsed = Date.now() - loopStartTime.value
            if (elapsed < loopDuration.value) {
                loopCount.value++
                currentRunningIndex.value = 1 // Skip #loop_time command
                addAssistantMessage(`⏱️ 定时循环 (已运行 ${Math.round(elapsed/1000)}s，第 ${loopCount.value} 次)`, undefined, 'executing')
                setTimeout(() => executeNextTaskInList(list, lines), 300)
                return
            }
        }
        
        // All done
        isRunningList.value = false
        currentRunningListId.value = null
        currentRunningIndex.value = -1
        processingListQueue.value = false
        const msg = loopCount.value > 1 ? `✅ 脚本执行完成 (共循环 ${loopCount.value} 次)` : '✅ 脚本执行完成'
        addAssistantMessage(msg, undefined, 'done')
        return
    }
    
    const cmdLine = lines[idx]
    const cmd = scriptToCommand(cmdLine)
    
    // Handle repeat commands
    if (cmd.type === 'repeat') {
        // Repeat next command N times
        const repeatN = parseInt(cmd.param1) || 1
        repeatMax.value = repeatN
        repeatCount.value = 0
        repeatStartIndex.value = idx + 1 // Next command
        addAssistantMessage(`🔁 准备重复下条命令 ${repeatN} 次`, undefined, 'executing')
        currentRunningIndex.value = idx + 1
        setTimeout(() => executeNextTaskInList(list, lines), 100)
        return
    }
    
    if (cmd.type === 'repeat_start') {
        repeatStartIndex.value = idx + 1
        repeatCount.value = 0
        currentRunningIndex.value = idx + 1
        setTimeout(() => executeNextTaskInList(list, lines), 100)
        return
    }
    
    if (cmd.type === 'repeat_end') {
        const param = cmd.param1 || '1'
        const isTimeMode = param.toLowerCase().endsWith('ms') || parseInt(param) > 1000
        
        if (isTimeMode) {
            // Time-based repeat
            const duration = parseInt(param.replace(/ms/i, '')) || 10000
            if (repeatStartTime.value === 0) {
                repeatStartTime.value = Date.now()
                repeatDuration.value = duration
            }
            
            const elapsed = Date.now() - repeatStartTime.value
            if (elapsed < repeatDuration.value) {
                repeatCount.value++
                addAssistantMessage(`📍 重复区块 (已运行 ${Math.round(elapsed/1000)}s，第 ${repeatCount.value} 次)`, undefined, 'executing')
                currentRunningIndex.value = repeatStartIndex.value
                setTimeout(() => executeNextTaskInList(list, lines), 100)
                return
            } else {
                // Done, reset and continue
                repeatStartTime.value = 0
                repeatDuration.value = 0
                repeatStartIndex.value = -1
                currentRunningIndex.value = idx + 1
                setTimeout(() => executeNextTaskInList(list, lines), 100)
                return
            }
        } else {
            // Count-based repeat
            const maxReps = parseInt(param) || 1
            repeatCount.value++
            if (repeatCount.value < maxReps) {
                addAssistantMessage(`📍 重复区块 (第 ${repeatCount.value + 1}/${maxReps} 次)`, undefined, 'executing')
                currentRunningIndex.value = repeatStartIndex.value
                setTimeout(() => executeNextTaskInList(list, lines), 100)
                return
            } else {
                // Done, reset and continue
                repeatCount.value = 0
                repeatStartIndex.value = -1
                currentRunningIndex.value = idx + 1
                setTimeout(() => executeNextTaskInList(list, lines), 100)
                return
            }
        }
    }
    
    // Check if we're in a single-command repeat
    if (repeatMax.value > 0 && repeatStartIndex.value === idx) {
        repeatCount.value++
        if (repeatCount.value <= repeatMax.value) {
            addUserMessage(`[脚本 ${idx+1}/${lines.length}] (重复 ${repeatCount.value}/${repeatMax.value}) ${cmdLine}`)
            Bridge.startTaskWithMode(cmdLine, executionMode.value)
            // Don't increment index, wait for watcher to handle
            return
        } else {
            // Done repeating
            repeatMax.value = 0
            repeatCount.value = 0
            repeatStartIndex.value = -1
            // Continue to next command
            currentRunningIndex.value = idx + 1
            setTimeout(() => executeNextTaskInList(list, lines), 100)
            return
        }
    }
    
    // Execute normal command
    addUserMessage(`[脚本 ${idx+1}/${lines.length}] ${cmdLine}`)
    Bridge.startTaskWithMode(cmdLine, executionMode.value)
}

// Watcher to handle queue progression
watch(isRunning, (newVal, oldVal) => {
    // If we are running a list, and the task just finished (running -> not running)
    if (isRunningList.value && processingListQueue.value && oldVal === true && newVal === false) {
        // Task finished.
        // Check if we're doing a single-command repeat
        if (repeatMax.value > 0 && repeatCount.value < repeatMax.value) {
            // Continue repeating same command
            setTimeout(() => {
                const list = customTaskLists.value.find(l => l.id === currentRunningListId.value)
                if (list) {
                    const lines = parseScriptLines(list.script)
                    executeNextTaskInList(list, lines)
                }
            }, 300)
            return
        }
        
        // Add a small delay then run next
        setTimeout(() => {
            currentRunningIndex.value++
            const list = customTaskLists.value.find(l => l.id === currentRunningListId.value)
            if (list) {
                const lines = parseScriptLines(list.script)
                executeNextTaskInList(list, lines)
            } else {
                // List disappeared? Stop.
                isRunningList.value = false
                processingListQueue.value = false
            }
        }, 500) // 0.5s delay between commands
    }
})

const stopTaskList = () => {
    isRunningList.value = false
    currentRunningListId.value = null
    processingListQueue.value = false
    stopTask() // stop current native task
}

// Import Logic
const openImportModal = () => {
    // Default to current session or first session
    if (currentSessionId.value) {
        importSelection.value.sessionId = currentSessionId.value
    } else if (chatSessions.value.length > 0) {
        importSelection.value.sessionId = chatSessions.value[0].id
    }
    importSelection.value.selectedIndices = new Set()
    showImportModal.value = true
}

const getSessionMessages = computed(() => {
    const session = chatSessions.value.find(s => s.id === importSelection.value.sessionId)
    if (!session) return []
    
    // Extract script commands (#tap, #swipe, etc.) from assistant messages
    const extractedCommands: { content: string, source: string }[] = []
    
    for (const msg of session.messages) {
        if (msg.role === 'assistant') {
            // Look for lines starting with # in the message content
            const lines = msg.content.split('\n')
            for (const line of lines) {
                const trimmed = line.trim()
                // Match valid script commands
                if (trimmed.match(/^#(tap|swipe|type|input|back|home|enter|longpress|doubletap|wait|keyevent|launch|shell|loop|loop_time|repeat|repeat_start|repeat_end)\b/i)) {
                    extractedCommands.push({
                        content: trimmed,
                        source: msg.content.substring(0, 30) + '...'
                    })
                }
            }
        }
    }
    
    return extractedCommands
})

const toggleImportSelection = (index: number) => {
    if (importSelection.value.selectedIndices.has(index)) {
        importSelection.value.selectedIndices.delete(index)
    } else {
        importSelection.value.selectedIndices.add(index)
    }
}

const importSelectedTasks = () => {
    const commands = getSessionMessages.value
    const selectedCmds = commands
        .filter((_, idx) => importSelection.value.selectedIndices.has(idx))
        .map(c => c.content)
        
    if (selectedCmds.length === 0) {
        alert('请至少选择一条命令')
        return
    }
    
    // Parse selected commands into structured format
    const newStructuredCmds = selectedCmds.map(scriptToCommand)
    editingCommands.value.push(...newStructuredCmds)
    
    // Also update the script string
    const currentScript = editingList.value.script.trim()
    editingList.value.script = currentScript 
        ? currentScript + '\n' + selectedCmds.join('\n')
        : selectedCmds.join('\n')
    showImportModal.value = false
}

// --- Script Helpers ---

const parseScriptLines = (script: string): string[] => {
    return script.split('\n')
        .map(l => l.trim())
        .filter(l => l.length > 0 && !l.startsWith('//') && !l.startsWith('--'))
}

const getScriptLineCount = (script: string): number => {
    return parseScriptLines(script).length
}

const getScriptPreview = (script: string): string => {
    const lines = parseScriptLines(script)
    if (lines.length <= 3) return lines.join('\n')
    return lines.slice(0, 3).join('\n') + '\n...'
}

// --- Helpers ---

const formatTime = (ts: number) => {
  const d = new Date(ts)
  return `${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}:${d.getSeconds().toString().padStart(2, '0')}`
}

const getLogColor = (type: string) => {
  switch (type) {
    case 'INFO': return 'text-blue-600 dark:text-blue-300'
    case 'ACTION': return 'text-[#00C853] font-bold'
    case 'WARNING': return 'text-amber-600 dark:text-amber-300'
    case 'ERROR': return 'text-red-600 dark:text-red-400 font-bold'
    default: return 'text-gray-600 dark:text-gray-300'
  }
}

const getLogBorderColor = (type: string) => {
  switch (type) {
    case 'INFO': return 'border-blue-400/30'
    case 'ACTION': return 'border-[#00C853]/50'
    case 'WARNING': return 'border-amber-500/50'
    case 'ERROR': return 'border-red-500/50'
    default: return 'border-gray-500/20'
  }
}

const getLogBadgeClass = (type: string) => {
  switch (type) {
    case 'INFO': return 'bg-blue-100 text-blue-700 dark:bg-blue-500/20 dark:text-blue-300'
    case 'ACTION': return 'bg-green-100 text-green-700 dark:bg-green-500/20 dark:text-green-300'
    case 'WARNING': return 'bg-amber-100 text-amber-700 dark:bg-amber-500/20 dark:text-amber-300'
    case 'ERROR': return 'bg-red-100 text-red-700 dark:bg-red-500/20 dark:text-red-300'
    default: return 'bg-gray-100 text-gray-700 dark:bg-gray-500/20 dark:text-gray-300'
  }
}

const formatDate = (ts: number) => {
  const d = new Date(ts)
  const month = (d.getMonth() + 1).toString().padStart(2, '0')
  const day = d.getDate().toString().padStart(2, '0')
  const hour = d.getHours().toString().padStart(2, '0')
  const min = d.getMinutes().toString().padStart(2, '0')
  return `${month}/${day} ${hour}:${min}`
}

const scrollToBottom = async () => {
  await nextTick()
  if (logContainer.value) {
    logContainer.value.scrollTop = logContainer.value.scrollHeight
  }
}

// --- Lifecycle ---

onMounted(() => {
    // Init Theme
    const savedTheme = localStorage.getItem('theme')
    if (savedTheme) {
        isDark.value = savedTheme === 'dark'
    } else {
        isDark.value = true // Default dark preference for this app
    }

  Bridge.onLogUpdate((newLogs: LogEntry[]) => {
    logs.value = newLogs
    scrollToBottom()
    
    // Convert logs to chat messages for display
    // Look for AI response patterns and extract them
    newLogs.forEach(log => {
      if (log.type === 'ACTION' && log.message.includes('执行:')) {
        // This is an action being executed - could add to chat UI
      }
    })
  })
  Bridge.onStatusUpdate((status: string) => {
    if (status === 'running') isRunning.value = true
    else if (status === 'completed' || status === 'cancelled' || status === 'error') isRunning.value = false
  })
  Bridge.onServiceStatusUpdate((enabled: boolean) => {
    serviceEnabled.value = enabled
  })
  Bridge.onPauseStateUpdate((paused: boolean) => {
    isPaused.value = paused
  })
  
  Bridge.onOpenSettings(() => {
    showSettings.value = true
  })
  
  // Handle Android back button
  Bridge.onBackPressed(() => {
    // Priority order: modals first, then panels
    if (showConfigModal.value) {
      showConfigModal.value = false
      return true
    }
    if (showLogViewer.value) {
      showLogViewer.value = false
      return true
    }
    if (showChatHistory.value) {
      showChatHistory.value = false
      return true
    }
    if (showSettings.value) {
      showSettings.value = false
      return true
    }
    return false // Allow default back behavior (exit app)
  })
  
  checkPermissions()
  checkShizukuStatus() // Check Shizuku status
  loadApiConfigs() // Load settings on start
  loadCommandHistory() // Load command history
  loadSettings() // Load log level, dev mode, chat history
  loadTaskLists() // Load custom task lists
  
  setInterval(checkPermissions, 2000)
  setInterval(checkShizukuStatus, 3000) // Periodically check Shizuku status
})
</script>

<style>
/* Custom Scrollbar */
.scrollbar-thin::-webkit-scrollbar {
  width: 5px;
  height: 5px;
}
.scrollbar-thin::-webkit-scrollbar-track {
  background: transparent;
}
.scrollbar-thin::-webkit-scrollbar-thumb {
  background: #ccc; /* Light mode default */
  border-radius: 99px;
}
.dark .scrollbar-thin::-webkit-scrollbar-thumb {
  background: #333; /* Dark mode */
}
.scrollbar-thin::-webkit-scrollbar-thumb:hover {
  background: #bbb;
}
.dark .scrollbar-thin::-webkit-scrollbar-thumb:hover {
  background: #444;
}

/* Animations */
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}
.animate-fade-in {
  animation: fadeIn 0.4s cubic-bezier(0.16, 1, 0.3, 1) forwards;
}

@keyframes fadeInDown {
  from { opacity: 0; transform: translateY(-20px); }
  to { opacity: 1; transform: translateY(0); }
}
.animate-fade-in-down {
  animation: fadeInDown 0.5s cubic-bezier(0.16, 1, 0.3, 1) forwards;
}

/* View Transitions */
.slide-fade-enter-active,
.slide-fade-leave-active {
  transition: opacity 0.2s ease;
}

.slide-fade-enter-from,
.slide-fade-leave-to {
  opacity: 0;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* Modal Animation */
.modal-enter-active,
.modal-leave-active {
  transition: all 0.3s ease;
}
.modal-enter-from,
.modal-leave-to {
  opacity: 0;
  transform: scale(0.95);
}

/* Slide-in Sidebar Animation */
.slide-in-enter-active,
.slide-in-leave-active {
  transition: all 0.3s ease;
}
.slide-in-enter-from {
  opacity: 0;
}
.slide-in-enter-from > div:last-child {
  transform: translateX(-100%);
}
.slide-in-leave-to {
  opacity: 0;
}
.slide-in-leave-to > div:last-child {
  transform: translateX(-100%);
}
</style>
