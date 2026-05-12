package com.nammahasiru.app.ui.addplant

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.nammahasiru.app.R
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.*

class AddPlantSuccessFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_add_plant_success, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val speciesName = arguments?.getString("speciesName") ?: "Unknown"
        val lat = arguments?.getFloat("latitude")?.toDouble() ?: 0.0
        val lng = arguments?.getFloat("longitude")?.toDouble() ?: 0.0

        view.findViewById<TextView>(R.id.tv_success_species).text = speciesName
        view.findViewById<TextView>(R.id.tv_success_location).text = "%.4f, %.4f".format(lat, lng)

        // Calculate 90 days from now
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, 90)
        val format = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        view.findViewById<TextView>(R.id.tv_success_due_date).text = format.format(calendar.time)

        // FloatingLeavesView handles its own animation, no need for manual start
        
        // Buttons
        view.findViewById<MaterialButton>(R.id.btn_back_to_home).setOnClickListener {
            // Pop back to home fragment
            findNavController().popBackStack(R.id.homeFragment, false)
        }

        view.findViewById<TextView>(R.id.btn_add_another).setOnClickListener {
            // Go back to the add plant fragment
            findNavController().popBackStack()
        }
    }
}
