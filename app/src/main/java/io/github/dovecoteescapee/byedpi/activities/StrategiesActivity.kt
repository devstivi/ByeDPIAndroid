package io.github.dovecoteescapee.byedpi.activities

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.github.dovecoteescapee.byedpi.R
import io.github.dovecoteescapee.byedpi.core.StrategyRepository
import io.github.dovecoteescapee.byedpi.data.Strategy
import io.github.dovecoteescapee.byedpi.databinding.ActivityStrategiesBinding
import io.github.dovecoteescapee.byedpi.databinding.ItemStrategyBinding
import io.github.dovecoteescapee.byedpi.data.AppStatus
import io.github.dovecoteescapee.byedpi.services.appStatus
import io.github.dovecoteescapee.byedpi.utility.getPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Этап 1 обмена стратегиями (issue #434 на оригинальном репозитории): показывает
 * курируемый, собранный из открытых issues список стратегий ByeDPI по операторам.
 * Ничего не отправляется с устройства — только чтение внешнего списка и локальное
 * применение выбранной записи по явному действию пользователя.
 */
class StrategiesActivity : AppCompatActivity() {
    private lateinit var binding: ActivityStrategiesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStrategiesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.strategies_title)

        loadStrategies()
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean =
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            true
        } else super.onOptionsItemSelected(item)

    private fun loadStrategies() {
        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) {
                runCatching { StrategyRepository.load(applicationContext) }.getOrNull()
            }

            if (list == null) {
                Toast.makeText(
                    this@StrategiesActivity,
                    R.string.strategies_load_failed,
                    Toast.LENGTH_LONG,
                ).show()
                return@launch
            }

            binding.strategiesSubtitle.text = getString(
                R.string.strategies_subtitle,
                list.updated ?: "?",
            )
            binding.strategiesList.adapter = StrategyAdapter(this@StrategiesActivity, list.strategies)
        }
    }

    private fun applyStrategy(strategy: Strategy) {
        val (status, _) = appStatus
        if (status != AppStatus.Halted) {
            Toast.makeText(this, R.string.settings_unavailable, Toast.LENGTH_SHORT).show()
            return
        }

        getPreferences().edit()
            .putBoolean("byedpi_enable_cmd_settings", true)
            .putString("byedpi_cmd_args", strategy.args)
            .apply()

        Toast.makeText(this, R.string.strategy_applied, Toast.LENGTH_SHORT).show()
    }

    private inner class StrategyAdapter(
        context: StrategiesActivity,
        items: List<Strategy>,
    ) : ArrayAdapter<Strategy>(context, 0, items) {
        override fun getView(position: Int, recycled: View?, parent: ViewGroup): View {
            val binding = if (recycled != null) {
                ItemStrategyBinding.bind(recycled)
            } else {
                ItemStrategyBinding.inflate(LayoutInflater.from(context), parent, false)
            }

            val strategy = getItem(position) ?: return binding.root

            binding.strategyOperators.text = strategy.operators
                .ifEmpty { listOf(getString(R.string.strategy_operator_generic)) }
                .joinToString(", ")
            binding.strategyArgs.text = strategy.args
            binding.strategyNotes.text = strategy.notes ?: ""
            binding.strategyNotes.visibility = if (strategy.notes.isNullOrBlank()) View.GONE else View.VISIBLE
            binding.strategyConfirmations.text =
                getString(R.string.strategy_confirmations, strategy.confirmations)
            binding.strategyApply.setOnClickListener { applyStrategy(strategy) }

            return binding.root
        }
    }
}
