package id.zenstars.rukunya.nativeapp.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "households", indices = [Index(value = ["kkNumber"], unique = true)])
data class HouseholdEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kkNumber: String,
    val headName: String,
    val address: String,
    val rt: String = "",
    val phone: String = "",
    val housingStatus: String = "Tetap",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "residents",
    foreignKeys = [ForeignKey(
        entity = HouseholdEntity::class,
        parentColumns = ["id"],
        childColumns = ["householdId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("householdId")]
)
data class ResidentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val householdId: Long,
    val name: String,
    val nik: String = "",
    val relation: String = "Anggota Keluarga",
    val gender: String = "",
    val birthDate: String = ""
)

@Entity(
    tableName = "contributions",
    foreignKeys = [ForeignKey(
        entity = HouseholdEntity::class,
        parentColumns = ["id"],
        childColumns = ["householdId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("householdId"), Index("period")]
)
data class ContributionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val householdId: Long,
    val period: String,
    val category: String,
    val amount: Long,
    val paidAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "cash_entries", indices = [Index("date")])
data class CashEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val amount: Long,
    val category: String,
    val note: String,
    val date: Long = System.currentTimeMillis()
)

@Entity(tableName = "announcements")
data class AnnouncementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "letters")
data class LetterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val residentName: String,
    val type: String,
    val purpose: String,
    val number: String,
    val status: String = "Diajukan",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "admins")
data class AdminProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val role: String,
    val phone: String = ""
)

data class HouseholdWithResidents(
    @Embedded val household: HouseholdEntity,
    @Relation(parentColumn = "id", entityColumn = "householdId")
    val residents: List<ResidentEntity>
)

@Dao
interface RukunyaDao {
    @Transaction
    @Query("SELECT * FROM households ORDER BY headName COLLATE NOCASE")
    fun observeHouseholds(): Flow<List<HouseholdWithResidents>>

    @Query("SELECT COUNT(*) FROM households")
    fun observeHouseholdCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM residents")
    fun observeResidentCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertHousehold(item: HouseholdEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResident(item: ResidentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContribution(item: ContributionEntity): Long

    @Query("SELECT * FROM contributions ORDER BY paidAt DESC")
    fun observeContributions(): Flow<List<ContributionEntity>>

    @Query("SELECT * FROM contributions WHERE period = :period ORDER BY paidAt DESC")
    fun observeContributionsForPeriod(period: String): Flow<List<ContributionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCash(item: CashEntryEntity): Long

    @Query("SELECT * FROM cash_entries ORDER BY date DESC")
    fun observeCash(): Flow<List<CashEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnouncement(item: AnnouncementEntity): Long

    @Query("SELECT * FROM announcements ORDER BY createdAt DESC")
    fun observeAnnouncements(): Flow<List<AnnouncementEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLetter(item: LetterEntity): Long

    @Query("SELECT * FROM letters ORDER BY createdAt DESC")
    fun observeLetters(): Flow<List<LetterEntity>>

    @Query("UPDATE letters SET status = :status WHERE id = :id")
    suspend fun updateLetterStatus(id: Long, status: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAdmin(item: AdminProfileEntity): Long

    @Query("SELECT * FROM admins ORDER BY name COLLATE NOCASE")
    fun observeAdmins(): Flow<List<AdminProfileEntity>>

    @Query("DELETE FROM households") suspend fun clearHouseholds()
    @Query("DELETE FROM residents") suspend fun clearResidents()
    @Query("DELETE FROM contributions") suspend fun clearContributions()
    @Query("DELETE FROM cash_entries") suspend fun clearCash()
    @Query("DELETE FROM announcements") suspend fun clearAnnouncements()
    @Query("DELETE FROM letters") suspend fun clearLetters()
    @Query("DELETE FROM admins") suspend fun clearAdmins()

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertHouseholds(items: List<HouseholdEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertResidents(items: List<ResidentEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertContributions(items: List<ContributionEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCashItems(items: List<CashEntryEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAnnouncements(items: List<AnnouncementEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertLetters(items: List<LetterEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAdmins(items: List<AdminProfileEntity>)

    @Query("SELECT * FROM households") suspend fun allHouseholds(): List<HouseholdEntity>
    @Query("SELECT * FROM residents") suspend fun allResidents(): List<ResidentEntity>
    @Query("SELECT * FROM contributions") suspend fun allContributions(): List<ContributionEntity>
    @Query("SELECT * FROM cash_entries") suspend fun allCash(): List<CashEntryEntity>
    @Query("SELECT * FROM announcements") suspend fun allAnnouncements(): List<AnnouncementEntity>
    @Query("SELECT * FROM letters") suspend fun allLetters(): List<LetterEntity>
    @Query("SELECT * FROM admins") suspend fun allAdmins(): List<AdminProfileEntity>
}

@Database(
    entities = [
        HouseholdEntity::class,
        ResidentEntity::class,
        ContributionEntity::class,
        CashEntryEntity::class,
        AnnouncementEntity::class,
        LetterEntity::class,
        AdminProfileEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class RukunyaDatabase : RoomDatabase() {
    abstract fun dao(): RukunyaDao

    companion object {
        @Volatile private var INSTANCE: RukunyaDatabase? = null

        fun get(context: Context): RukunyaDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                RukunyaDatabase::class.java,
                "rukunya_native_v2.db"
            ).build().also { INSTANCE = it }
        }
    }
}
