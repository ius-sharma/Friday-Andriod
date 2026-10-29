package com.personal.friday.meals

import android.content.Context
import android.content.SharedPreferences
import java.util.Random

object MealDialogueGenerator {

    const val MEAL_BREAKFAST = "BREAKFAST"
    const val MEAL_BREAKFAST_CHECK = "BREAKFAST_CHECK"
    const val MEAL_LUNCH = "LUNCH"
    const val MEAL_SNACKS = "SNACKS"
    const val MEAL_DINNER = "DINNER"

    private val breakfastDialogues = listOf(
        "Good morning Sir! Subah ke 7 baj chuke hain, breakfast ready kar lijiye. Aaj ke missions lambe hain, Arc Reactor ko full fuel chahiye.",
        "Sir, subah ke 7 baj gaye hain. Nashta skip karna Stark protocol ke khilaf hai. Fresh breakfast le lijiye Sir.",
        "Sir, systems online hain aur subah ka energy level replenish hone ka time aa gaya hai. Chaliye, healthy breakfast kar lijiye.",
        "Morning Sir! 7 AM reached. Coding aur work sprint start karne se pehle solid breakfast zaroori hai. Core vitals 100% honi chahiye.",
        "Sir, 7 AM breakfast protocol initiated! Thoda fresh fruit ya healthy nashta le lijiye, taaki pure din peak focus rahe.",
        "Sir, aapka assistant Friday yaad dila rahi hai, subah ka nashta sabse zaroori meal hai. Please 7 baje breakfast kar lijiye Sir.",
        "Good morning Sir! Dimag aur body dono ko high performance fuel chahiye. Chaliye, aaram se nashta kijiye.",
        "Sir, subah ki chai aur nashta table par invite kar raha hai. 10 minute ka breakfast break lijiye Sir."
    )

    private val breakfastCheckDialogues = listOf(
        "Sir, subah ke 8 baj chuke hain. Umeed hai aapne nashta kar liya hoga. Agar abhi tak nahi kiya, toh please thoda breakfast zaroor le lijiye Sir.",
        "Sir, 8 AM follow-up check! Umeed hai aapka morning breakfast complete ho gaya hoga. Healthy nashte ke bina kaam start mat kijiye Sir.",
        "Sir, Friday morning check-in: 8 baj gaye hain, umeed hai aapne nashta finish kar liya hoga? Vitals peak par hone chahiye.",
        "Sir, subah ka 8 baj chuka hai. Hope aapne breakfast skip nahi kiya aur acche se nashta kar liya hoga Sir.",
        "Sir, 8 AM status verification: Umeed hai aapne swadisht nashta kar liya hoga. Ab fresh energy ke sath aaj ke goals target karte hain Sir.",
        "Sir, morning follow-up! Umeed hai breakfast done hai Sir. Agar bacha hai toh 5 minute me finish kar lijiye taaki energy bani rahe."
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
        "Sir, shaam ke 7:30 baj chuke hain. Early dinner protocol active hai! Kaam wrap up kijiye aur aaram se dinner kar lijiye.",
        "Sir, 7:30 PM is ideal for dinner. Pure din ki mehnat ke baad body ko early dinner aur digestion relaxation chahiye.",
        "Sir, late night dinner health ke liye accha nahi hota. 7:30 baje aaram se swadisht khana kha lijiye Sir.",
        "Sir, systems winding down. Kaam safe state me save kijiye, ab dinner table par shift hone ka order hai Sir!",
        "Sir, 7:30 PM reached. Raat ka khana skip mat kijiye. Delicious dinner ke sath full recharge ho jaiye Sir.",
        "Sir, Friday calling for Dinner! 7:30 baj gaye hain. Coding window minimize kijiye aur poshtik dinner kar lijiye Sir.",
        "Sir, early dinner best habit hai. Chaliye, parivar ke sath baithein aur sukoon se 7:30 baje dinner enjoy karein."
    )

    fun getNextDialogue(context: Context, mealType: String): String {
        val prefs: SharedPreferences = context.getSharedPreferences("friday_prefs", Context.MODE_PRIVATE)
        val list = when (mealType.uppercase()) {
            MEAL_BREAKFAST -> breakfastDialogues
            MEAL_BREAKFAST_CHECK -> breakfastCheckDialogues
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
            MEAL_BREAKFAST_CHECK -> "Breakfast Follow-up (Nashta Check)"
            MEAL_LUNCH -> "Lunch (Dopahar ka Khana)"
            MEAL_SNACKS -> "Evening Snacks (Chai/Nashta)"
            MEAL_DINNER -> "Dinner (Raat ka Khana)"
            else -> "Meal"
        }
    }
}
