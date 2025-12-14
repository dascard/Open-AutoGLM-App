package com.autoglm.app

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.autoglm.app.core.AIProvider
import com.autoglm.app.core.ApiConfig
import com.autoglm.app.data.PreferencesManager
import com.autoglm.app.databinding.ActivitySettingsBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefsManager: PreferencesManager
    private lateinit var adapter: ApiConfigAdapter
    
    private var retryCount = 3
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings_title)
        
        prefsManager = PreferencesManager(this)
        
        setupRecyclerView()
        loadSettings()
        setupClickListeners()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
    
    private fun setupRecyclerView() {
        adapter = ApiConfigAdapter(
            onEdit = { config -> showEditDialog(config) },
            onDelete = { config -> showDeleteConfirmation(config) },
            onToggle = { config, enabled -> 
                prefsManager.updateApiConfig(config.copy(enabled = enabled))
                refreshList()
            }
        )
        binding.rvApiConfigs.layoutManager = LinearLayoutManager(this)
        binding.rvApiConfigs.adapter = adapter
    }
    
    private fun loadSettings() {
        refreshList()
        retryCount = prefsManager.maxRetries
        binding.tvRetryCount.text = retryCount.toString()
    }
    
    private fun refreshList() {
        val configs = prefsManager.apiConfigs
        adapter.submitList(configs)
        binding.tvEmptyHint.visibility = if (configs.isEmpty()) View.VISIBLE else View.GONE
    }
    
    private fun setupClickListeners() {
        binding.btnAddApi.setOnClickListener {
            showAddDialog()
        }
        
        binding.btnRetryMinus.setOnClickListener {
            if (retryCount > 1) {
                retryCount--
                binding.tvRetryCount.text = retryCount.toString()
                prefsManager.maxRetries = retryCount
            }
        }
        
        binding.btnRetryPlus.setOnClickListener {
            if (retryCount < 10) {
                retryCount++
                binding.tvRetryCount.text = retryCount.toString()
                prefsManager.maxRetries = retryCount
            }
        }
    }
    
    private fun showAddDialog() {
        showApiDialog(null)
    }
    
    private fun showEditDialog(config: ApiConfig) {
        showApiDialog(config)
    }
    
    private fun showApiDialog(existingConfig: ApiConfig?) {
        val isEdit = existingConfig != null
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_api, null)
        
        val etName = dialogView.findViewById<EditText>(R.id.etName)
        val spinnerProvider = dialogView.findViewById<Spinner>(R.id.spinnerProvider)
        val spinnerModel = dialogView.findViewById<Spinner>(R.id.spinnerModel)
        val layoutCustomModel = dialogView.findViewById<TextInputLayout>(R.id.layoutCustomModel)
        val etCustomModel = dialogView.findViewById<EditText>(R.id.etCustomModel)
        val layoutCustomEndpoint = dialogView.findViewById<TextInputLayout>(R.id.layoutCustomEndpoint)
        val etCustomEndpoint = dialogView.findViewById<EditText>(R.id.etCustomEndpoint)
        val etApiKey = dialogView.findViewById<EditText>(R.id.etApiKey)
        
        // 设置服务商列表
        val providers = AIProvider.entries.toTypedArray()
        val providerAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            providers.map { it.displayName }
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerProvider.adapter = providerAdapter
        
        // 当前选中的模型列表
        var currentModels = listOf<String>()
        var currentModelIds = listOf<String>()
        
        // 更新模型列表
        fun updateModelSpinner(provider: AIProvider) {
            val models = provider.defaultModels
            currentModelIds = if (models.isEmpty()) {
                listOf("custom")
            } else {
                models.map { it.id } + "custom"
            }
            currentModels = if (models.isEmpty()) {
                listOf("自定义模型")
            } else {
                models.map { "${it.displayName}${if (!it.supportsVision) " ⚠️不支持视觉" else ""}" } + "自定义模型..."
            }
            
            val modelAdapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                currentModels
            ).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            spinnerModel.adapter = modelAdapter
            
            // 显示/隐藏自定义端点
            layoutCustomEndpoint.visibility = 
                if (provider == AIProvider.OPENAI_COMPATIBLE) View.VISIBLE else View.GONE
        }
        
        // 监听服务商选择
        spinnerProvider.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateModelSpinner(providers[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        // 监听模型选择
        spinnerModel.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val isCustom = currentModelIds.getOrNull(position) == "custom"
                layoutCustomModel.visibility = if (isCustom) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        // 如果是编辑，填充现有数据
        if (existingConfig != null) {
            etName.setText(existingConfig.name)
            spinnerProvider.setSelection(providers.indexOf(existingConfig.provider))
            etApiKey.setText(existingConfig.apiKey)
            etCustomEndpoint.setText(existingConfig.customEndpoint ?: "")
            
            // 延迟设置模型，因为需要等 spinner 更新
            spinnerProvider.post {
                val modelIndex = currentModelIds.indexOf(existingConfig.model)
                if (modelIndex >= 0) {
                    spinnerModel.setSelection(modelIndex)
                } else {
                    // 自定义模型
                    spinnerModel.setSelection(currentModelIds.size - 1)
                    etCustomModel.setText(existingConfig.model)
                }
            }
        }
        
        AlertDialog.Builder(this)
            .setTitle(if (isEdit) "编辑 API" else "添加 API")
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                val name = etName.text.toString().trim().ifEmpty { "我的 API" }
                val provider = providers[spinnerProvider.selectedItemPosition]
                val modelPosition = spinnerModel.selectedItemPosition
                val isCustomModel = currentModelIds.getOrNull(modelPosition) == "custom"
                val model = if (isCustomModel) {
                    etCustomModel.text.toString().trim()
                } else {
                    currentModelIds.getOrNull(modelPosition) ?: ""
                }
                val apiKey = etApiKey.text.toString().trim()
                val customEndpoint = etCustomEndpoint.text.toString().trim()
                
                // 验证
                if (model.isEmpty()) {
                    Toast.makeText(this, "请选择或输入模型", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (apiKey.isEmpty()) {
                    Toast.makeText(this, "请输入 API Key", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (provider == AIProvider.OPENAI_COMPATIBLE && customEndpoint.isEmpty()) {
                    Toast.makeText(this, "请输入 API 端点", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                val config = ApiConfig(
                    id = existingConfig?.id ?: java.util.UUID.randomUUID().toString(),
                    name = name,
                    provider = provider,
                    model = model,
                    apiKey = apiKey,
                    customEndpoint = if (provider == AIProvider.OPENAI_COMPATIBLE) customEndpoint else null,
                    enabled = existingConfig?.enabled ?: true,
                    priority = existingConfig?.priority ?: 0
                )
                
                if (isEdit) {
                    prefsManager.updateApiConfig(config)
                } else {
                    prefsManager.addApiConfig(config)
                }
                
                refreshList()
                Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showDeleteConfirmation(config: ApiConfig) {
        AlertDialog.Builder(this)
            .setTitle("删除 API")
            .setMessage("确定要删除 \"${config.name}\" 吗？")
            .setPositiveButton("删除") { _, _ ->
                prefsManager.removeApiConfig(config.id)
                refreshList()
                Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }
}

/**
 * API 配置列表适配器
 */
class ApiConfigAdapter(
    private val onEdit: (ApiConfig) -> Unit,
    private val onDelete: (ApiConfig) -> Unit,
    private val onToggle: (ApiConfig, Boolean) -> Unit
) : RecyclerView.Adapter<ApiConfigAdapter.ViewHolder>() {
    
    private var items = listOf<ApiConfig>()
    
    fun submitList(list: List<ApiConfig>) {
        items = list
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_api_config, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }
    
    override fun getItemCount() = items.size
    
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvName)
        private val tvProvider: TextView = itemView.findViewById(R.id.tvProvider)
        private val switchEnabled: Switch = itemView.findViewById(R.id.switchEnabled)
        private val btnEdit: MaterialButton = itemView.findViewById(R.id.btnEdit)
        private val btnDelete: MaterialButton = itemView.findViewById(R.id.btnDelete)
        
        fun bind(config: ApiConfig) {
            tvName.text = config.name
            tvProvider.text = config.displayInfo
            switchEnabled.isChecked = config.enabled
            
            switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                onToggle(config, isChecked)
            }
            
            btnEdit.setOnClickListener { onEdit(config) }
            btnDelete.setOnClickListener { onDelete(config) }
        }
    }
}
