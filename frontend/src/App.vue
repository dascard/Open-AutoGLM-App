<template>
  <div class="min-h-screen transition-colors duration-300 ease-in-out flex flex-col gap-4 p-4 font-sans bg-gray-50 text-gray-900 dark:bg-[#121212] dark:text-gray-200">
    
    <!-- 1. 顶部标题栏 & 导航 -->
    <div class="flex items-center justify-between px-2 py-1">
      <div class="flex items-center gap-3">
        <div class="w-1.5 h-6 rounded-full shadow-sm bg-[#00C853] shadow-[0_0_8px_rgba(0,200,83,0.5)]"></div>
        <h1 class="text-xl font-bold tracking-wide transition-colors text-gray-800 dark:text-white">AutoGLM Hybrid</h1>
      </div>
      
      <div class="flex items-center gap-2">
        <!-- 模型配置按钮 -->
        <button 
          @click="showSettings = true"
          class="flex items-center justify-center w-9 h-9 rounded-lg transition-all active:scale-95 bg-white text-gray-600 border border-gray-200 hover:bg-gray-100 dark:bg-white/5 dark:text-gray-300 dark:border-white/10 dark:hover:bg-white/10"
          title="模型配置"
        >
          <svg xmlns="http://www.w3.org/2000/svg" class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/><circle cx="12" cy="12" r="3"/></svg>
        </button>

        <!-- 主题切换按钮 -->
        <button 
          @click="toggleTheme"
          class="flex items-center justify-center w-9 h-9 rounded-lg transition-all active:scale-95 bg-white text-amber-500 border border-gray-200 hover:bg-gray-100 dark:bg-white/5 dark:text-yellow-300 dark:border-white/10 dark:hover:bg-white/10"
          :title="isDark ? '切换到日间模式' : '切换到夜间模式'"
        >
          <svg v-if="isDark" xmlns="http://www.w3.org/2000/svg" class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="5"/><line x1="12" y1="1" x2="12" y2="3"/><line x1="12" y1="21" x2="12" y2="23"/><line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/><line x1="1" y1="12" x2="3" y2="12"/><line x1="21" y1="12" x2="23" y2="12"/><line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/></svg>
          <svg v-else xmlns="http://www.w3.org/2000/svg" class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/></svg>
        </button>

        <!-- 服务状态指示器 -->
        <div 
          class="flex items-center justify-center w-9 h-9 rounded-lg transition-all border"
          :class="serviceEnabled ? 'bg-[#00C853]/10 border-[#00C853]/30' : 'bg-red-500/10 border-red-500/30'"
          :title="serviceEnabled ? '服务运行中' : '服务未运行'"
        >
          <div class="relative flex h-3 w-3">
            <span v-if="serviceEnabled" class="animate-ping absolute inline-flex h-full w-full rounded-full bg-[#00C853] opacity-75"></span>
            <span class="relative inline-flex rounded-full h-3 w-3" :class="serviceEnabled ? 'bg-[#00C853]' : 'bg-red-500'"></span>
          </div>
        </div>
      </div>
    </div>

    <!-- 2. 模式选择和权限引导卡片 -->
    <div v-if="!showSettings" class="rounded-xl p-0 shadow-lg overflow-hidden animate-fade-in-down border bg-white border-gray-200 dark:bg-[#1E1E1E] dark:border-white/5">
      <!-- 执行模式选择 -->
      <div class="px-4 py-3 border-b flex items-center justify-between bg-gray-50 border-gray-200 dark:bg-[#181818] dark:border-white/5">
        <span class="font-bold text-sm text-gray-700 dark:text-gray-200">执行模式</span>
        <div class="flex bg-gray-200 dark:bg-gray-700 rounded-lg p-0.5">
          <button 
            @click="executionMode = 'accessibility'"
            class="px-3 py-1 text-xs font-medium rounded-md transition-all"
            :class="executionMode === 'accessibility' ? 'bg-white dark:bg-gray-600 text-gray-800 dark:text-white shadow-sm' : 'text-gray-500 dark:text-gray-400'"
          >
            无障碍服务
          </button>
          <button 
            @click="executionMode = 'shizuku'"
            class="px-3 py-1 text-xs font-medium rounded-md transition-all"
            :class="executionMode === 'shizuku' ? 'bg-white dark:bg-gray-600 text-gray-800 dark:text-white shadow-sm' : 'text-gray-500 dark:text-gray-400'"
          >
            Shizuku ADB
          </button>
        </div>
      </div>
      
      <div class="p-4 flex flex-col gap-3">
        <!-- 无障碍服务模式 -->
        <template v-if="executionMode === 'accessibility'">
          <!-- 无障碍权限项 -->
          <div class="flex items-center justify-between p-3 rounded-lg border transition-colors bg-gray-50 border-gray-200 dark:bg-[#121212] dark:border-white/5">
            <div class="flex items-center gap-3">
              <div class="p-2 rounded-lg bg-blue-500/10 text-blue-600 dark:text-blue-400">
                <svg xmlns="http://www.w3.org/2000/svg" class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><path d="m4.93 4.93 14.14 14.14"/><path d="m14.83 9.17-5.66 5.66"/></svg>
              </div>
              <div class="flex flex-col">
                <span class="text-sm font-medium text-gray-900 dark:text-white">无障碍服务</span>
                <span class="text-xs text-gray-500">用于模拟点击和获取屏幕信息</span>
              </div>
            </div>
            <button 
              v-if="!serviceEnabled"
              @click="openAccessibility"
              class="px-3 py-1.5 bg-blue-600 hover:bg-blue-500 text-white text-xs font-medium rounded-md transition-colors shadow-sm"
            >
              去开启
            </button>
            <div v-else class="flex items-center gap-1 text-[#00C853] text-xs font-medium px-2">
              <svg xmlns="http://www.w3.org/2000/svg" class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 6 9 17l-5-5"/></svg>
              已开启
            </div>
          </div>
        </template>

        <!-- Shizuku ADB 模式 -->
        <template v-else>
          <!-- Shizuku 状态项 -->
          <div class="flex items-center justify-between p-3 rounded-lg border transition-colors bg-gray-50 border-gray-200 dark:bg-[#121212] dark:border-white/5">
            <div class="flex items-center gap-3">
              <div class="p-2 rounded-lg bg-purple-500/10 text-purple-600 dark:text-purple-400">
                <svg xmlns="http://www.w3.org/2000/svg" class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m6 9 6 6 6-6"/></svg>
              </div>
              <div class="flex flex-col">
                <span class="text-sm font-medium text-gray-900 dark:text-white">Shizuku 服务</span>
                <span class="text-xs text-gray-500">{{ shizukuStatus.available ? (shizukuStatus.hasPermission ? '已就绪' : '需要授权') : '请先启动 Shizuku 应用' }}</span>
              </div>
            </div>
            <div v-if="shizukuStatus.available && shizukuStatus.hasPermission" class="flex items-center gap-1 text-[#00C853] text-xs font-medium px-2">
              <svg xmlns="http://www.w3.org/2000/svg" class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 6 9 17l-5-5"/></svg>
              已就绪
            </div>
            <button 
              v-else-if="shizukuStatus.available && !shizukuStatus.hasPermission"
              @click="requestShizukuPermission"
              class="px-3 py-1.5 bg-purple-600 hover:bg-purple-500 text-white text-xs font-medium rounded-md transition-colors shadow-sm"
            >
              授权
            </button>
            <div v-else class="flex items-center gap-1 text-red-500 text-xs font-medium px-2">
              <svg xmlns="http://www.w3.org/2000/svg" class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>
              未启动
            </div>
          </div>

          <!-- 提示 -->
          <div class="text-xs text-gray-500 dark:text-gray-400 px-1">
            💡 请先安装 Shizuku 应用，通过无线调试或 ADB 启动服务
          </div>
        </template>
      </div>
    </div>

    <!-- 悬浮窗权限提示 -->
    <div v-if="!showSettings" class="rounded-lg p-3 border flex items-center justify-between" :class="overlayPermissionValid ? 'bg-green-50 border-green-200 dark:bg-green-500/10 dark:border-green-500/20' : 'bg-amber-50 border-amber-200 dark:bg-amber-500/10 dark:border-amber-500/20'">
      <div class="flex items-center gap-2">
        <svg xmlns="http://www.w3.org/2000/svg" class="w-4 h-4" :class="overlayPermissionValid ? 'text-green-600 dark:text-green-400' : 'text-amber-600 dark:text-amber-400'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"/><line x1="9" y1="3" x2="9" y2="21"/></svg>
        <span class="text-xs" :class="overlayPermissionValid ? 'text-green-800 dark:text-green-200' : 'text-amber-800 dark:text-amber-200'">
          {{ overlayPermissionValid ? '悬浮窗权限已开启' : '开启悬浮窗可显示实时状态' }}
        </span>
      </div>
      <template v-if="!overlayPermissionValid">
        <button 
          @click="requestOverlayPermission"
          class="px-2 py-1 text-xs font-medium text-amber-700 hover:text-amber-900 dark:text-amber-300 dark:hover:text-amber-100"
        >
          去开启 →
        </button>
      </template>
      <div v-else class="flex items-center gap-1 text-green-600 dark:text-green-400 text-xs font-medium">
        <svg xmlns="http://www.w3.org/2000/svg" class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 6 9 17l-5-5"/></svg>
        已开启
      </div>
    </div>

    <!-- 3. 主界面 & 4. API/模型页面 切换动画 -->
    <Transition name="slide-fade" mode="out-in">
        <!-- Main Dashboard -->
        <div v-if="!showSettings" key="main" class="flex flex-col gap-4 flex-1 min-h-0">
            <!-- 任务输入卡片 -->
            <div class="rounded-xl p-5 shadow-sm border flex flex-col gap-4 animate-fade-in transition-colors bg-white border-gray-200 dark:bg-[#1E1E1E] dark:border-white/5" style="animation-delay: 0.1s;">
              <div class="flex justify-between items-center">
                <label class="text-sm font-bold flex items-center gap-2 text-gray-700 dark:text-gray-300">
                  <svg xmlns="http://www.w3.org/2000/svg" class="w-4 h-4 text-[#00C853]" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 20h9"/><path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"/></svg>
                  任务指令
                </label>
                <button v-if="taskSchema" @click="taskSchema = ''" class="text-xs text-gray-500 hover:text-gray-800 dark:hover:text-white transition-colors flex items-center gap-1">
                  <svg xmlns="http://www.w3.org/2000/svg" class="w-3 h-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M18 6 6 18"/><path d="m6 6 12 12"/></svg>
                  清空
                </button>
              </div>
              <div class="relative">
                <textarea 
                  v-model="taskSchema" 
                  class="w-full rounded-lg p-3 text-base leading-relaxed focus:outline-none focus:ring-1 transition-all resize-none min-h-[100px] bg-gray-50 border border-gray-300 text-gray-900 placeholder-gray-400 focus:border-[#00C853] focus:ring-[#00C853] dark:bg-[#121212] dark:border-white/10 dark:text-white dark:placeholder-gray-600"
                  placeholder="请输入需要执行的任务，例如：打开微信给文件传输助手发送你好..."
                ></textarea>
              </div>
              
              <!-- 历史命令选择 -->
              <div v-if="commandHistory.length > 0" class="flex items-center gap-2 min-w-0">
                <span class="text-xs text-gray-500 dark:text-gray-400 whitespace-nowrap flex-shrink-0">历史:</span>
                <select 
                  @change="selectHistoryCommand(($event.target as HTMLSelectElement).value); ($event.target as HTMLSelectElement).value = ''"
                  class="flex-1 min-w-0 text-sm rounded-lg p-2 bg-gray-50 border border-gray-300 text-gray-900 focus:border-[#00C853] focus:ring-1 focus:ring-[#00C853] dark:bg-[#121212] dark:border-white/10 dark:text-white"
                  style="max-width: 100%;"
                >
                  <option value="">选择历史命令...</option>
                  <option v-for="(cmd, index) in commandHistory" :key="index" :value="cmd">{{ truncateCommand(cmd, 30) }}</option>
                </select>
                <button 
                  @click="clearHistory"
                  class="p-2 rounded-lg text-gray-400 hover:text-red-500 hover:bg-red-50 dark:hover:bg-red-500/10 transition-colors"
                  title="清除历史记录"
                >
                  <svg xmlns="http://www.w3.org/2000/svg" class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 6h18"/><path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"/><path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"/></svg>
                </button>
              </div>

              <!-- 操作按钮组 -->
              <div class="grid grid-cols-3 gap-3">
                <button 
                  @click="startTask" 
                  :disabled="isRunning || !taskSchema"
                  class="bg-[#00C853] hover:bg-[#00E676] disabled:opacity-30 disabled:cursor-not-allowed text-white dark:text-black font-bold py-3 px-4 rounded-lg shadow-md active:scale-95 transition-all flex items-center justify-center gap-2 group"
                >
                  <svg xmlns="http://www.w3.org/2000/svg" class="w-5 h-5 transition-transform group-hover:-translate-y-0.5 group-active:translate-y-0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="5 3 19 12 5 21 5 3"/></svg>
                  开始
                </button>
                
                <button 
                  @click="togglePause" 
                  :disabled="!isRunning"
                  class="bg-[#FFB300] hover:bg-[#FFCA28] disabled:opacity-20 disabled:cursor-not-allowed text-black font-bold py-3 px-4 rounded-lg shadow-md active:scale-95 transition-all flex items-center justify-center gap-2"
                >
                  <template v-if="isPaused">
                    <svg xmlns="http://www.w3.org/2000/svg" class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="5 3 19 12 5 21 5 3"/></svg>
                    恢复
                  </template>
                  <template v-else>
                    <svg xmlns="http://www.w3.org/2000/svg" class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="6" y="4" width="4" height="16"/><rect x="14" y="4" width="4" height="16"/></svg>
                    暂停
                  </template>
                </button>
                
                <button 
                  @click="stopTask" 
                  :disabled="!isRunning"
                  class="bg-[#FF5252] hover:bg-[#FF8A80] disabled:opacity-20 disabled:cursor-not-allowed text-white dark:text-black font-bold py-3 px-4 rounded-lg shadow-md active:scale-95 transition-all flex items-center justify-center gap-2"
                >
                  <svg xmlns="http://www.w3.org/2000/svg" class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"/></svg>
                  停止
                </button>
              </div>
            </div>

            <!-- 日志终端卡片 -->
            <div class="flex-1 rounded-xl border flex flex-col overflow-hidden min-h-0 shadow-sm relative animate-fade-in transition-colors bg-white border-gray-200 dark:bg-[#1E1E1E] dark:border-white/5" style="animation-delay: 0.2s;">
              <div class="px-4 py-3 border-b flex justify-between items-center transition-colors bg-gray-50 border-gray-200 dark:bg-[#181818] dark:border-white/5">
                <div class="flex items-center gap-2">
                    <svg xmlns="http://www.w3.org/2000/svg" class="w-4 h-4 text-gray-500" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="4 17 10 11 4 5"/><line x1="12" y1="19" x2="20" y2="19"/></svg>
                    <span class="text-xs font-bold tracking-wider text-gray-500 uppercase">Terminal Output</span>
                    <span v-if="isRunning" class="w-2 h-2 rounded-full bg-green-500 animate-pulse shadow-[0_0_8px_rgba(34,197,94,0.6)]"></span>
                </div>
                <button @click="clearLogs" class="text-xs text-gray-500 hover:text-gray-800 dark:hover:text-white px-2 py-1 rounded hover:bg-black/5 dark:hover:bg-white/5 transition-colors flex items-center gap-1">
                  <svg xmlns="http://www.w3.org/2000/svg" class="w-3 h-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 6h18"/><path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"/><path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"/></svg>
                  清除
                </button>
              </div>
              
              <div 
                ref="logContainer" 
                class="flex-1 overflow-y-auto p-4 font-mono text-xs sm:text-sm space-y-2 scrollbar-thin scrollbar-thumb-gray-400 dark:scrollbar-thumb-gray-700 scrollbar-track-transparent bg-white dark:bg-[#1E1E1E]"
              >
                <div v-if="logs.length === 0" class="h-full flex flex-col items-center justify-center text-gray-400 dark:text-gray-700 space-y-3 opacity-60">
                  <svg xmlns="http://www.w3.org/2000/svg" class="w-12 h-12 stroke-[1.5]" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m5 11 4-7"/><path d="m19 13-4 7"/><path d="M8 19h8"/></svg>
                  <div class="font-medium">等待执行任务...</div>
                </div>
                
                <div v-for="(log, index) in logs" :key="index" class="break-words leading-relaxed animate-fade-in pl-1 border-l-2" :class="getLogBorderColor(log.type)">
                  <div class="flex gap-2">
                    <span class="text-gray-400 dark:text-gray-500 text-[10px] mt-0.5 select-none font-medium opacity-70 w-[50px]">{{ formatTime(log.timestamp) }}</span>
                    <span :class="getLogColor(log.type)" class="flex-1 whitespace-pre-wrap">
                      {{ log.message }}
                    </span>
                  </div>
                </div>
              </div>
            </div>
        </div>

        <!-- 4. 模型配置页面 (Settings Page) -->
        <div v-else key="settings" class="flex flex-col gap-4 flex-1 min-h-0 animate-fade-in-down">
            <!-- 头部 -->
            <div class="flex items-center gap-3 pb-2 border-b transition-colors border-gray-200 dark:border-white/5">
                <button @click="showSettings = false" class="p-1 rounded-full hover:bg-black/5 dark:hover:bg-white/10">
                    <svg xmlns="http://www.w3.org/2000/svg" class="w-6 h-6 text-gray-500 dark:text-gray-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m15 18-6-6 6-6"/></svg>
                </button>
                <h2 class="text-lg font-bold text-gray-800 dark:text-white">API 模型配置</h2>
                <div class="flex-1"></div>
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
const executionMode = ref<'accessibility' | 'shizuku'>('accessibility')
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
    if (!serviceEnabled.value) { alert("请先开启无障碍服务"); openAccessibility(); return }
  } else {
    if (!shizukuStatus.value.available) { alert("请先启动 Shizuku 应用"); return }
    if (!shizukuStatus.value.hasPermission) { alert("请先授权 Shizuku 权限"); requestShizukuPermission(); return }
    // 自动绑定 UserService
    if (!shizukuStatus.value.serviceBound) {
      bindShizukuService()
    }
  }
  
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
    if (confirm('确定要清除所有历史命令吗？')) {
        Bridge.clearCommandHistory()
        commandHistory.value = []
    }
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
  
  checkPermissions()
  checkShizukuStatus() // Check Shizuku status
  loadApiConfigs() // Load settings on start
  loadCommandHistory() // Load command history
  
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
  transition: all 0.3s ease-out;
}

.slide-fade-enter-from {
  opacity: 0;
  transform: translateX(20px);
}

.slide-fade-leave-to {
  opacity: 0;
  transform: translateX(-20px);
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
</style>
