package com.sentinel.quantum.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sentinel.quantum.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComplianceScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.compliance_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ComplianceItem(
                title = stringResource(R.string.compliance_gdpr_title),
                description = stringResource(R.string.compliance_gdpr)
            )
            
            ComplianceItem(
                title = stringResource(R.string.compliance_data_title),
                description = stringResource(R.string.compliance_data)
            )
            
            ComplianceItem(
                title = stringResource(R.string.compliance_sovereignty_title),
                description = stringResource(R.string.compliance_sovereignty)
            )
            
            ComplianceItem(
                title = stringResource(R.string.compliance_transparency_title),
                description = stringResource(R.string.compliance_transparency)
            )
            
            ComplianceItem(
                title = stringResource(R.string.compliance_permissions_title),
                description = stringResource(R.string.compliance_permissions)
            )
            
            ComplianceItem(
                title = stringResource(R.string.compliance_license_title),
                description = stringResource(R.string.compliance_license)
            )
        }
    }
}

@Composable
fun ComplianceItem(title: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
