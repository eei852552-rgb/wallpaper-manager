@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.wallpapermanager.app

import android.app.WallpaperManager
import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { WallpaperApp() } }
}

private val categories = listOf("Nature","Anime","Cars","Technology","Abstract","Animals","Space","Games","Minimal","4K")
private enum class Page { Login, Home, Detail, Profile, Admin }

@Composable fun WallpaperApp() {
    val context=LocalContext.current; var configured by remember { mutableStateOf(false) }; var page by remember { mutableStateOf(Page.Login) }; var selected by remember { mutableStateOf<Wallpaper?>(null) }; var dark by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { configured=FirebaseSetup.initialize(context) }
    MaterialTheme(colorScheme=if(dark) darkColorScheme() else lightColorScheme()) {
        if(!configured) SetupScreen()
        else when(page) { Page.Login -> LoginScreen { page=Page.Home }; Page.Home -> HomeScreen({ selected=it; page=Page.Detail }, { page=Page.Profile }, { page=Page.Admin }, { dark=!dark }); Page.Detail -> selected?.let { DetailScreen(it) { page=Page.Home } }; Page.Profile -> ProfileScreen { page=Page.Home }; Page.Admin -> AdminScreen { page=Page.Home } }
    }
}

@Composable private fun SetupScreen() { Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment=Alignment.Center) { Column(horizontalAlignment=Alignment.CenterHorizontally) { Text("Wallpaper Manager", style=MaterialTheme.typography.headlineMedium, fontWeight=FontWeight.Bold); Spacer(Modifier.height(12.dp)); Text("Firebase is not configured yet. Add your Firebase values in app/src/main/res/values/strings.xml, then rebuild.", color=MaterialTheme.colorScheme.onSurfaceVariant) } } }

@Composable private fun LoginScreen(onSuccess:()->Unit) {
    val auth=remember { FirebaseAuth.getInstance() }; var register by remember { mutableStateOf(false) }; var email by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }; var error by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }; val scope=rememberCoroutineScope()
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment=Alignment.Center) { Column(verticalArrangement=Arrangement.spacedBy(14.dp)) { Text("Wallpaper Manager", style=MaterialTheme.typography.headlineLarge, fontWeight=FontWeight.Bold); Text(if(register) "Create your account" else "Welcome back", color=MaterialTheme.colorScheme.onSurfaceVariant); OutlinedTextField(email,{email=it},label={Text("Email")},singleLine=true,modifier=Modifier.fillMaxWidth()); OutlinedTextField(password,{password=it},label={Text("Password")},singleLine=true,visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth()); if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error); Button(enabled=!busy,onClick={ scope.launch { busy=true; try { if(register) { val result=auth.createUserWithEmailAndPassword(email.trim(),password).await(); result.user?.let { FirebaseRepository.createProfile(it.uid,it.email ?: "") } } else auth.signInWithEmailAndPassword(email.trim(),password).await(); onSuccess() } catch(e:Exception) { error=e.message ?: "Authentication failed" } finally { busy=false } } },modifier=Modifier.fillMaxWidth()) { Text(if(busy) "Please wait…" else if(register) "Register" else "Log in") }; TextButton(onClick={register=!register}) { Text(if(register) "Already have an account? Log in" else "Create an account") } } }
}

@Composable private fun HomeScreen(onOpen:(Wallpaper)->Unit,onProfile:()->Unit,onAdmin:()->Unit,onTheme:()->Unit) {
    val repo=remember { WallpaperRepository() }; val scope=rememberCoroutineScope(); var list by remember { mutableStateOf<List<Wallpaper>>(emptyList()) }; var search by remember { mutableStateOf("") }; var category by remember { mutableStateOf<String?>(null) }; var profile by remember { mutableStateOf<UserProfile?>(null) }; var loading by remember { mutableStateOf(true) }; var error by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { try { list=repo.wallpapers(); profile=repo.profile() } catch(e:Exception) { error=e.message ?: "Could not load wallpapers" } finally { loading=false } }
    val shown=list.filter { (category==null || it.category==category) && (search.isBlank() || it.title.contains(search,true) || it.category.contains(search,true)) }
    Scaffold(topBar={TopAppBar(title={Text("Wallpaper Manager",fontWeight=FontWeight.Bold)},actions={IconButton(onClick=onTheme){Icon(Icons.Default.DarkMode,"Toggle theme")};IconButton(onClick=onProfile){Icon(Icons.Default.Person,"Profile")};if(profile?.isAdmin==true) IconButton(onClick=onAdmin){Icon(Icons.Default.AdminPanelSettings,"Admin")}})},bottomBar={NavigationBar{NavigationBarItem(selected=true,onClick={},icon={Icon(Icons.Default.Home,"Home")},label={Text("Home")});NavigationBarItem(selected=false,onClick=onProfile,icon={Icon(Icons.Default.Person,"Profile")},label={Text("Profile")})}}) { pad -> Column(Modifier.padding(pad).padding(horizontal=16.dp)) { OutlinedTextField(search,{search=it},modifier=Modifier.fillMaxWidth(),singleLine=true,placeholder={Text("Search wallpapers")},leadingIcon={Icon(Icons.Default.Search,null)}); Spacer(Modifier.height(14.dp)); Text("Featured",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold); val featured=list.filter{it.isFeatured}; if(featured.isNotEmpty()) LazyColumn(Modifier.height(150.dp),horizontalAlignment=Alignment.CenterHorizontally){items(featured.take(5)){WallpaperCard(it,onOpen,Modifier.fillMaxWidth().padding(vertical=4.dp))}} else Text("New wallpapers will appear here",color=MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(12.dp)); Text("Categories",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold); Row(Modifier.fillMaxWidth().padding(vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){categories.take(5).forEach{FilterChip(selected=category==it,onClick={category=if(category==it)null else it},label={Text(it)})}}; Spacer(Modifier.height(4.dp)); if(loading) Box(Modifier.fillMaxWidth().height(240.dp),contentAlignment=Alignment.Center){CircularProgressIndicator()} else if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error) else if(shown.isEmpty()) Box(Modifier.fillMaxWidth().height(240.dp),contentAlignment=Alignment.Center){Text("No wallpapers found") } else LazyVerticalGrid(columns=GridCells.Fixed(2),contentPadding=PaddingValues(bottom=80.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){items(shown){WallpaperCard(it,onOpen,Modifier.fillMaxWidth())}} } }
}

@Composable private fun WallpaperCard(w:Wallpaper,onOpen:(Wallpaper)->Unit,modifier:Modifier=Modifier) { Card(modifier.clickable{onOpen(w)},shape=RoundedCornerShape(16.dp)){Column{WallpaperImage(w,Modifier.fillMaxWidth().height(170.dp));Row(Modifier.fillMaxWidth().padding(10.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(w.title,fontWeight=FontWeight.SemiBold,maxLines=1);Text(w.category,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};if(w.isVip) AssistChip(onClick={},label={Text("VIP")},leadingIcon={Icon(Icons.Default.Lock,null,Modifier.size(14.dp))})}}} }

@Composable private fun DetailScreen(w:Wallpaper,onBack:()->Unit) { val context=LocalContext.current; val repo=remember{WallpaperRepository()}; val scope=rememberCoroutineScope(); var favorite by remember{mutableStateOf(false)}; var vip by remember{mutableStateOf(false)}; var message by remember{mutableStateOf("")}; var showChoices by remember{mutableStateOf(false)}; LaunchedEffect(Unit){favorite=repo.isFavorite(w.id);vip=repo.profile()?.isVip==true}; Column(Modifier.fillMaxSize()){TopAppBar(title={Text(w.title)},navigationIcon={IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Back")}},actions={IconButton(onClick={scope.launch{favorite=!favorite;repo.setFavorite(w.id,favorite)}}){Icon(if(favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,"Favorite")}}); LazyColumn{item{WallpaperImage(w,Modifier.fillMaxWidth().height(360.dp));Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text(w.title,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text("${w.category}  •  ${if(w.isVip) "VIP" else "Free"}",color=MaterialTheme.colorScheme.onSurfaceVariant);if(w.isVip&&!vip) Text("VIP membership is required to download or set this wallpaper.",color=MaterialTheme.colorScheme.error);Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Button(enabled=!w.isVip||vip,onClick={scope.launch{try{downloadImage(context,repo,w);message="Saved to Pictures/Wallpaper Manager"}catch(e:Exception){message=e.message?:"Download failed"}}}){Icon(Icons.Default.Download,null);Spacer(Modifier.width(6.dp));Text("Download")};Button(enabled=!w.isVip||vip,onClick={showChoices=true}){Icon(Icons.Default.Wallpaper,null);Spacer(Modifier.width(6.dp));Text("Set")}};if(message.isNotBlank())Text(message,color=MaterialTheme.colorScheme.primary)}}}};if(showChoices)AlertDialog(onDismissRequest={showChoices=false},title={Text("Set wallpaper")},text={Column{TextButton(onClick={scope.launch{setWallpaper(context,repo,w,WallpaperManager.FLAG_SYSTEM);showChoices=false}}){Text("Home Screen")};TextButton(onClick={scope.launch{setWallpaper(context,repo,w,WallpaperManager.FLAG_LOCK);showChoices=false}}){Text("Lock Screen")};TextButton(onClick={scope.launch{setWallpaper(context,repo,w,WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK);showChoices=false}}){Text("Both")}}},confirmButton={TextButton(onClick={showChoices=false}){Text("Cancel")}})}

@Composable private fun ProfileScreen(onBack:()->Unit) { val auth=remember{FirebaseAuth.getInstance()}; val scope=rememberCoroutineScope(); var profile by remember{mutableStateOf<UserProfile?>(null)}; LaunchedEffect(Unit){profile=WallpaperRepository().profile()}; Column(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Back")};Text("Your profile",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold);Text(auth.currentUser?.email ?: "");Card{Column(Modifier.padding(18.dp)){Text(if(profile?.isVip==true) "VIP member" else "Free member",fontWeight=FontWeight.Bold);Text(if(profile?.isVip==true) "You can access all wallpapers." else "Upgrade to VIP to unlock premium wallpapers.")}};Button(onClick={auth.signOut();onBack()}){Text("Log out")}} }

@Composable private fun AdminScreen(onBack:()->Unit) {
    val context=LocalContext.current; val repo=remember{WallpaperRepository()}; val scope=rememberCoroutineScope()
    var uri by remember{mutableStateOf<android.net.Uri?>(null)}; var title by remember{mutableStateOf("")}; var cat by remember{mutableStateOf("Nature")}
    var vip by remember{mutableStateOf(false)}; var featured by remember{mutableStateOf(false)}; var published by remember{mutableStateOf(true)}
    var wallpapers by remember{mutableStateOf<List<Wallpaper>>(emptyList())}; var status by remember{mutableStateOf("")}; var busy by remember{mutableStateOf(false)}
    var confirmDelete by remember{mutableStateOf<Wallpaper?>(null)}; var deleting by remember{mutableStateOf(false)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri=it}
    LaunchedEffect(Unit) { runCatching { repo.wallpapers() }.onSuccess { wallpapers=it }.onFailure { status=it.message ?: "Could not load wallpapers" } }
    Column(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        TopAppBar(title={Text("Admin dashboard")},navigationIcon={IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Back")}})
        Button(onClick={picker.launch("image/*")}){Text(if(uri==null) "Select image" else "Image selected")}
        OutlinedTextField(title,{title=it},label={Text("Wallpaper title")},modifier=Modifier.fillMaxWidth())
        Text("Category")
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){categories.take(4).forEach{FilterChip(selected=cat==it,onClick={cat=it},label={Text(it)})}}
        Row(verticalAlignment=Alignment.CenterVertically){Checkbox(vip,{vip=it});Text("VIP wallpaper")}
        Row(verticalAlignment=Alignment.CenterVertically){Checkbox(featured,{featured=it});Text("Featured")}
        Row(verticalAlignment=Alignment.CenterVertically){Checkbox(published,{published=it});Text("Published")}
        Button(enabled=uri!=null&&!busy&&!deleting,onClick={scope.launch{busy=true;try{repo.upload(context,uri!!,title,cat,vip,featured,published);wallpapers=repo.wallpapers();status="Uploaded successfully"}catch(e:Exception){status=e.message?:"Upload failed"}finally{busy=false}}}){Text(if(busy)"Uploading…" else "Upload wallpaper")}
        Text("Existing wallpapers",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
        LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth().weight(1f)) {
            items(wallpapers,key={it.id}) { wallpaper ->
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)){Text(wallpaper.title,fontWeight=FontWeight.SemiBold);Text(if(wallpaper.isPublished) "Published" else "Unpublished",style=MaterialTheme.typography.labelSmall)}
                    TextButton(enabled=!deleting,onClick={confirmDelete=wallpaper}){Text("Delete")}
                }
            }
        }
        Text(status,color=if(status.contains("failed",true)||status.contains("could not",true))MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
    }
    confirmDelete?.let { wallpaper ->
        AlertDialog(onDismissRequest={if(!deleting)confirmDelete=null},title={Text("Delete wallpaper?")},text={Text("This removes ${wallpaper.title} from Firestore and ImageKit.")},dismissButton={TextButton(enabled=!deleting,onClick={confirmDelete=null}){Text("Cancel")}},confirmButton={TextButton(enabled=!deleting,onClick={scope.launch{deleting=true;try{repo.delete(wallpaper.id);wallpapers=wallpapers.filterNot{it.id==wallpaper.id};status="Deleted successfully"}catch(e:Exception){status=e.message?:"Delete failed"}finally{deleting=false;confirmDelete=null}}}){Text(if(deleting)"Deleting…" else "Delete")}})
    }
}

@Composable
private fun WallpaperImage(w: Wallpaper, modifier: Modifier = Modifier) {
    val repo = remember { WallpaperRepository() }
    var bitmap by remember(w.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(w.id, w.imageUrl, w.fileId) {
        bitmap = runCatching { ImageCompression.decodeForWallpaper(repo.loadImageBytes(w)) }.getOrNull()
    }
    when {
        bitmap != null -> Image(bitmap!!.asImageBitmap(), w.title, modifier, contentScale = ContentScale.Crop)
        w.imageUrl.isNotBlank() -> AsyncImage(model=w.imageUrl, contentDescription=w.title, modifier=modifier, contentScale=ContentScale.Crop)
        else -> Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment=Alignment.Center) {
            if (w.isVip) Icon(Icons.Default.Lock, contentDescription="VIP wallpaper locked") else CircularProgressIndicator()
        }
    }
}

private suspend fun downloadImage(context: android.content.Context, repo: WallpaperRepository, wallpaper: Wallpaper) {
    withContext(Dispatchers.IO) {
        val bytes = repo.loadImageBytes(wallpaper)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "${wallpaper.title}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Wallpaper Manager")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create gallery item")
        try { context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Could not write gallery item") }
        catch (e: Exception) { context.contentResolver.delete(uri, null, null); throw e }
    }
}

private suspend fun setWallpaper(context: android.content.Context, repo: WallpaperRepository, wallpaper: Wallpaper, flags: Int) {
    withContext(Dispatchers.IO) {
        val bitmap = ImageCompression.decodeForWallpaper(repo.loadImageBytes(wallpaper))
        try { WallpaperManager.getInstance(context).setBitmap(bitmap, null, true, flags) }
        finally { bitmap.recycle() }
    }
}

private object FirebaseRepository { suspend fun createProfile(uid:String,email:String){com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(uid).set(mapOf("uid" to uid,"email" to email,"isAdmin" to false,"isVip" to false,"createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp())).await()} }
