var VolunteersMatchingHelper = {};

jQuery(window).load(function() {
	jQuery('select', '.proposedVolunteers').each(function() {
		jQuery(this).dblclick(function(event) {
			var selectedOption = event.target;
			if (selectedOption == null)
				return;
			
			if (jQuery(this).hasClass('selector-prototype'))
				return;
			
			jQuery('select', '.assignedVolunteers').each(function() {
				var filterExpression = "option[value='" + selectedOption.value + "']";
				if (jQuery(filterExpression, jQuery(this)).length == 0) {
					var option = '<option selected="true" class="selector-item" value="' + selectedOption.value + '" style="">' + jQuery(selectedOption).text() + '</option>';
					jQuery(this).append(option);
				}
				jQuery("option[value='']", jQuery(this)).each(function() {
					this.selected = false;
				})
				jQuery(this).trigger('change');
			});
		});
	});
	
	jQuery('select', '.assignedVolunteers').each(function() {
		jQuery(this).dblclick(function(event) {
			var selectedOption = event.target;
			if (selectedOption == null)
				return;
			
			if (selectedOption.value != '') {
				selectedOption.selected = false;
				jQuery(selectedOption).remove();
				jQuery(this).trigger('change');
			}

			jQuery(this).trigger('change');
		});
	});
});