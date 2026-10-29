package com.personal.friday.meals

import android.content.Context
import android.content.SharedPreferences
import java.util.Random

object MealDialogueGenerator {

    const val MEAL_BREAKFAST = "BREAKFAST"
    const val MEAL_LUNCH = "LUNCH"
    const val MEAL_SNACKS = "SNACKS"
    const val MEAL_DINNER = "DINNER"

    private val breakfastDialogues = listOf(
        "Good morning Sir! Subah ka breakfast ready kar lijiye. Aaj ke missions lambe hain, Arc Reactor ko full fuel chahiye.",
        "Sir, subah ke 9 baj chuke hain. Nashta skip karna Stark protocol ke khilaf hai. Fresh breakfast le lijiye Sir.",
        "Sir, systems online hain aur aapka energy level replenish hone ka time aa gaya hai. Chaliye, healthy breakfast kar lijiye.",
        "Morning Sir! Coding aur work start karne se pehle solid breakfast zaroori hai. Core vitals 100% honi chahiye.",
        "Sir, breakfast protocol initiated! Thoda fresh fruit ya healthy nashta le lijiye, taaki pure din peak focus rahe.",
        "Sir, aapka assistant Friday yaad dila rahi hai, subah ka nashta sabse zaroori meal hai. Please breakfast kar lijiye.",
        "Good morning Sir! Dimag aur body dono ko high performance fuel chahiye. Chaliye, aaram se nashta kijiye.",
        "Sir, subah ki chai aur nashta table par invite kar raha hai. 10 minute ka breakfast break lijiye Sir."
    )

    private val lunchDialogues = listOf(
        "Sir, dopahar ke 1:30 baj gaye hain. Dopahar ka lunch protocol initiate ho chuka hai. Screen chhodkar khana kha lijiye.",
        "Sir, kafi der se focus mode me hain. Brain CPU ko cooling aur lunch fuel ki zaroorat hai. Chaliye lunch karte hain Sir.",
        "Sir, dopahar ka khana ready hoga. Kaam pause kijiye aur aaram se lunch enjoy kijiye. Friday baki sab sambhal legi.",
        "Sir, Stark Industries rule number one: No coding on an empty stomach! Dopahar ka lunch complete kar lijiye Sir.",
        "Sir, 1:30 PM reached. Energy levels dip hone se pehle balanced lunch le lijiye. Health comes first Sir.",
        "Sir, dopahar ki bhook lag rahi hogi. Screen se nazar hatayein aur pet puja kar lijiye. Chaliye lunch time!",
        "Sir, Bio-Fuel replenishment zaroori hai. Dopahar ka swadisht khana kijiye aur recharge ho jaiye.",
        "Sir, dopahar ka lunch time ho gaya hai. 15 minute ka proper meal break lijiye, productivity double ho jayegi Sir."
    )

    private val snacksDialogues = listOf(
        "Sir, shaam ke 5:30 baj gaye hain. Shaam ki kadak chai aur light snacks ka perfect waqt hai Sir.",
        "Sir, shaam ki battery down hone se pehle thoda chai ya coffee aur snacks le lijiye. Quick energy booster chahiye Sir.",
        "Sir, shaam ka break interval active hai. Screen se stretch lijiye aur kuch fresh snacks kha lijiye.",
        "Sir, continuous focus ke baad 10 minute ka chai aur snacks interval banta hai. Friday recommends a quick recharge!",
        "Sir, shaam ho chuki hai. Thoda refresh ho jaiye, chai ya healthy snack le lijiye taaki evening sprint smooth rahe.",
        "Sir, shaam ke snacks ka time! Ek garam cup chai aur snacks ke sath thoda fresh air le lijiye Sir.",
        "Sir, afternoon se non-stop active hain aap. 5:30 baj chuke hain, snacks break lena mandatory hai Sir."
    )

    private val dinnerDialogues = listOf(
        "Sir, raat ke 9 baj chuke hain. Dinner protocol active hai! Kaam wrap up kijiye aur aaram se dinner kar lijiye.",
        "Sir, pure din ki mehnat ke baad ab body ko proper dinner aur relaxation chahiye. Chaliye raat ka khana kha lijiye.",
        "Sir, late night food digestion ke liye accha nahi hota. 9 PM is ideal. Aaram se swadisht dinner kijiye Sir.",
        "Sir, systems winding down. Kaam safe state me hai, ab dinner table par shift hone ka order hai Sir!",
        "Sir, raat ka khana skip mat kijiye. Full day battery drain ho chuki hai, delicious dinner ke sath recharge ho jaiye.",
        "Sir, 9 baj gaye hain. Dinner time Sir! Thoda parivar ke sath baithein aur sukoon se khana enjoy karein.",
        "Sir, Friday calling for Dinner! Coding window minimize kijiye aur raat ka poshtik khana le lijiye Sir."
    )

    fun getNextDialogue(context: Context, mealType: String): String {
        val prefs: SharedPreferences = context.getSharedPreferences("friday_prefs", Context.MODE_PRIVATE)
        val list = when (mealType.uppercase()) {
            MEAL_BREAKFAST -> breakfastDialogues
            MEAL_LUNCH -> lunchDialogues
            MEAL_SNACKS -> snacksDialogues
            MEAL_DINNER -> dinnerDialogues
            else -> lunchDialogues
        }

        val key = "last_meal_index_${mealType.lowercase()}"
        val lastIndex = prefs.getInt(key, -1)

        val random = Random()
        var newIndex = random.nextInt(list.size)
        if (list.size > 1 && newIndex == lastIndex) {
            newIndex = (newIndex + 1) % list.size
        }

        prefs.edit().putInt(key, newIndex).apply()
        return list[newIndex]
    }

    fun getMealDisplayName(mealType: String): String {
        return when (mealType.uppercase()) {
            MEAL_BREAKFAST -> "Breakfast (Nashta)"
            MEAL_LUNCH -> "Lunch (Dopahar ka Khana)"
            MEAL_SNACKS -> "Evening Snacks (Chai/Nashta)"
            MEAL_DINNER -> "Dinner (Raat ka Khana)"
            else -> "Meal"
        }
    }
}
